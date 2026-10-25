package com.app.easypharma_backend.domain.search.geo;

import com.app.easypharma_backend.domain.medication.entity.PharmacyMedication;
import com.app.easypharma_backend.domain.pharmacy.entity.Pharmacy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@Profile("!sqlserver")
@RequiredArgsConstructor
public class PostgisGeoSearchRepository implements GeoSearchRepository {

    @PersistenceContext
    private EntityManager em;

    private final GeoEntityLoader loader;

    @Override
    @SuppressWarnings("unchecked")
    public List<Pharmacy> findNearbyPharmacies(double latitude, double longitude, double radiusKm) {
        List<UUID> ids = em.createNativeQuery("""
                SELECT p.id FROM pharmacies p
                WHERE p.status = 'APPROVED'
                AND ST_DistanceSphere(
                    ST_MakePoint(CAST(p.longitude AS DOUBLE PRECISION), CAST(p.latitude AS DOUBLE PRECISION)),
                    ST_MakePoint(:longitude, :latitude)
                ) <= :radiusKm * 1000
                ORDER BY ST_DistanceSphere(
                    ST_MakePoint(CAST(p.longitude AS DOUBLE PRECISION), CAST(p.latitude AS DOUBLE PRECISION)),
                    ST_MakePoint(:longitude, :latitude)
                )
                """, UUID.class)
                .setParameter("latitude", latitude)
                .setParameter("longitude", longitude)
                .setParameter("radiusKm", radiusKm)
                .getResultList();
        return loader.pharmacies(ids);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<PharmacyMedication> searchMedicationsNearest(String query, String therapeuticClass,
            double latitude, double longitude) {
        List<UUID> ids = em.createNativeQuery("""
                SELECT pm.id FROM pharmacy_medications pm
                JOIN medications m ON pm.medication_id = m.id
                JOIN pharmacies p ON pm.pharmacy_id = p.id
                WHERE pm.is_available = true
                AND p.status = 'APPROVED'
                AND (
                    m.name ILIKE '%' || CAST(:query AS text) || '%'
                    OR m.generic_name ILIKE '%' || CAST(:query AS text) || '%'
                    OR m.symptoms ILIKE '%' || CAST(:query AS text) || '%'
                )
                AND (CAST(:therapeuticClass AS text) IS NULL OR m.therapeutic_class = CAST(:therapeuticClass AS text))
                ORDER BY ST_DistanceSphere(
                    ST_MakePoint(CAST(p.longitude AS DOUBLE PRECISION), CAST(p.latitude AS DOUBLE PRECISION)),
                    ST_MakePoint(:userLon, :userLat)
                ) ASC
                """, UUID.class)
                .setParameter("query", query)
                .setParameter("therapeuticClass", therapeuticClass)
                .setParameter("userLat", latitude)
                .setParameter("userLon", longitude)
                .getResultList();
        return loader.offers(ids);
    }
}
