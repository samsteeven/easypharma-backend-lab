package com.app.easypharma_backend.domain.search.geo;

import com.app.easypharma_backend.domain.medication.entity.PharmacyMedication;
import com.app.easypharma_backend.domain.pharmacy.entity.Pharmacy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Équivalent SQL Server de la recherche PostGIS : colonne calculée {@code location}
 * de type geography (SRID 4326, voir migration V2) et méthode STDistance, qui renvoie des mètres.
 * Attention à l'ordre des arguments : {@code geography::Point(latitude, longitude, srid)},
 * alors que {@code ST_MakePoint(longitude, latitude)} côté PostGIS.
 */
@Repository
@Profile("sqlserver")
public class SqlServerGeoSearchRepository implements GeoSearchRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    @SuppressWarnings("unchecked")
    public List<Pharmacy> findNearbyPharmacies(double latitude, double longitude, double radiusKm) {
        return em.createNativeQuery("""
                SELECT p.* FROM pharmacies p
                WHERE p.status = 'APPROVED'
                AND p.location.STDistance(geography::Point(:latitude, :longitude, 4326)) <= :radiusKm * 1000
                ORDER BY p.location.STDistance(geography::Point(:latitude, :longitude, 4326))
                """, Pharmacy.class)
                .setParameter("latitude", latitude)
                .setParameter("longitude", longitude)
                .setParameter("radiusKm", radiusKm)
                .getResultList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<PharmacyMedication> searchMedicationsNearest(String query, String therapeuticClass,
            double latitude, double longitude) {
        return em.createNativeQuery("""
                SELECT pm.* FROM pharmacy_medications pm
                JOIN medications m ON pm.medication_id = m.id
                JOIN pharmacies p ON pm.pharmacy_id = p.id
                WHERE pm.is_available = 1
                AND p.status = 'APPROVED'
                AND (
                    LOWER(m.name) LIKE LOWER(CONCAT('%', CAST(:query AS varchar(255)), '%'))
                    OR LOWER(m.generic_name) LIKE LOWER(CONCAT('%', CAST(:query AS varchar(255)), '%'))
                    OR LOWER(m.symptoms) LIKE LOWER(CONCAT('%', CAST(:query AS varchar(255)), '%'))
                )
                AND (CAST(:therapeuticClass AS varchar(50)) IS NULL
                     OR m.therapeutic_class = CAST(:therapeuticClass AS varchar(50)))
                ORDER BY p.location.STDistance(geography::Point(:userLat, :userLon, 4326)) ASC
                """, PharmacyMedication.class)
                .setParameter("query", query)
                .setParameter("therapeuticClass", therapeuticClass)
                .setParameter("userLat", latitude)
                .setParameter("userLon", longitude)
                .getResultList();
    }
}
