package com.app.easypharma_backend.domain.search.geo;

import com.app.easypharma_backend.domain.medication.entity.PharmacyMedication;
import com.app.easypharma_backend.domain.pharmacy.entity.Pharmacy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat commun de la recherche géographique : les mêmes assertions doivent passer
 * avec l'implémentation PostGIS et avec l'implémentation SQL Server (STDistance).
 */
@Transactional
abstract class GeoSearchContract {

    // Position de l'utilisateur : Yaoundé. Trois pharmacies approuvées (Centre, Bastos, Akwa à Douala)
    // et une pharmacie en attente d'approbation, qui ne doit jamais ressortir.
    private static final double USER_LAT = 3.855;
    private static final double USER_LON = 11.51;

    @Autowired
    private GeoSearchRepository geoSearch;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM pharmacy_medications");
        jdbc.update("DELETE FROM pharmacies");
        jdbc.update("DELETE FROM medications");
        jdbc.update("DELETE FROM users");

        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        UUID medication = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO medications (id, requires_prescription, created_at, updated_at,
                                         therapeutic_class, name, generic_name, symptoms)
                VALUES (?, ?, ?, ?, 'ANTALGIQUE', 'Doliprane', 'Paracétamol', 'fièvre, douleur')
                """, medication, false, now, now);

        pharmacy(1, "Centre", 3.8667, 11.5167, "APPROVED", medication, now);
        pharmacy(2, "Bastos", 3.8480, 11.5021, "APPROVED", medication, now);
        pharmacy(3, "Akwa", 4.0511, 9.7679, "APPROVED", medication, now);
        pharmacy(4, "En attente", 3.8600, 11.5200, "PENDING", medication, now);
    }

    private void pharmacy(int n, String name, double lat, double lon, String status, UUID medication, Timestamp now) {
        UUID user = UUID.randomUUID();
        UUID pharmacy = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO users (id, is_active, is_verified, created_at, updated_at, phone, role,
                                   first_name, last_name, email, password)
                VALUES (?, ?, ?, ?, ?, ?, 'PHARMACY_ADMIN', 'T', 'A', ?, 'x')
                """, user, true, true, now, now, "6900000" + n, "a" + n + "@test.local");
        jdbc.update("""
                INSERT INTO pharmacies (id, user_id, latitude, longitude, created_at, updated_at, phone, status,
                                        license_number, city, name, license_document_url, address)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'Yaoundé', ?, 'u', 'adresse')
                """, pharmacy, user, lat, lon, now, now, "2" + n, status, "L" + n, name);
        jdbc.update("""
                INSERT INTO pharmacy_medications (id, is_available, price, stock_quantity, created_at, updated_at,
                                                  medication_id, pharmacy_id)
                VALUES (?, ?, 1000, 10, ?, ?, ?, ?)
                """, UUID.randomUUID(), true, now, now, medication, pharmacy);
    }

    private static List<String> names(List<Pharmacy> pharmacies) {
        return pharmacies.stream().map(Pharmacy::getName).toList();
    }

    @Test
    void nearbyPharmaciesAreSortedByDistanceWithinRadius() {
        assertThat(names(geoSearch.findNearbyPharmacies(USER_LAT, USER_LON, 10)))
                .containsExactly("Bastos", "Centre");
    }

    @Test
    void nearbyPharmaciesWithLargeRadiusIncludeDoualaButNotPendingOnes() {
        assertThat(names(geoSearch.findNearbyPharmacies(USER_LAT, USER_LON, 400)))
                .containsExactly("Bastos", "Centre", "Akwa");
    }

    @Test
    void nearbyPharmaciesOutsideRadiusAreEmpty() {
        assertThat(geoSearch.findNearbyPharmacies(USER_LAT, USER_LON, 0.5)).isEmpty();
    }

    @Test
    void medicationSearchIsSortedByDistanceFromUser() {
        List<PharmacyMedication> fromYaounde = geoSearch.searchMedicationsNearest("PARACÉTAMOL", null, USER_LAT, USER_LON);
        assertThat(fromYaounde).extracting(pm -> pm.getPharmacy().getName())
                .containsExactly("Bastos", "Centre", "Akwa");

        List<PharmacyMedication> fromDouala = geoSearch.searchMedicationsNearest("doli", "ANTALGIQUE", 4.05, 9.77);
        assertThat(fromDouala.get(0).getPharmacy().getName()).isEqualTo("Akwa");
    }

    @Test
    void medicationSearchHonoursTherapeuticClassAndSymptoms() {
        assertThat(geoSearch.searchMedicationsNearest("fièvre", "ANTIBIOTIQUE", USER_LAT, USER_LON)).isEmpty();
        assertThat(geoSearch.searchMedicationsNearest("fièvre", "ANTALGIQUE", USER_LAT, USER_LON)).hasSize(3);
    }
}
