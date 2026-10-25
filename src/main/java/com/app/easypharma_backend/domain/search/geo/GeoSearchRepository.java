package com.app.easypharma_backend.domain.search.geo;

import com.app.easypharma_backend.domain.medication.entity.PharmacyMedication;
import com.app.easypharma_backend.domain.pharmacy.entity.Pharmacy;

import java.util.List;

/**
 * Requêtes géographiques : le SQL spatial diffère d'une base à l'autre
 * (PostGIS : ST_DistanceSphere ; SQL Server : type geography et STDistance).
 * Une implémentation par profil Spring : {@code !sqlserver} et {@code sqlserver}.
 */
public interface GeoSearchRepository {

    /** Pharmacies approuvées dans un rayon (km), de la plus proche à la plus lointaine. */
    List<Pharmacy> findNearbyPharmacies(double latitude, double longitude, double radiusKm);

    /** Offres disponibles correspondant à la recherche, triées par distance croissante. */
    List<PharmacyMedication> searchMedicationsNearest(String query, String therapeuticClass,
            double latitude, double longitude);
}
