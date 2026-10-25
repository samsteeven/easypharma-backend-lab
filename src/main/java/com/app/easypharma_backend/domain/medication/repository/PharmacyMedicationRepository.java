package com.app.easypharma_backend.domain.medication.repository;

import com.app.easypharma_backend.domain.medication.entity.PharmacyMedication;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PharmacyMedicationRepository extends JpaRepository<PharmacyMedication, UUID> {

        // Recherche par pharmacie
        List<PharmacyMedication> findByPharmacyId(UUID pharmacyId);

        // Recherche par médicament
        List<PharmacyMedication> findByMedicationId(UUID medicationId);

        // Recherche par pharmacie et médicament
        Optional<PharmacyMedication> findByPharmacyIdAndMedicationId(UUID pharmacyId, UUID medicationId);

        @Lock(LockModeType.PESSIMISTIC_WRITE) // Empêche tout autre thread de lire/écrire pendant la transaction
        @Query("SELECT pm FROM PharmacyMedication pm WHERE pm.pharmacy.id = :pId AND pm.medication.id = :mId")
        Optional<PharmacyMedication> findByPharmacyIdAndMedicationIdForUpdate(@Param("pId") UUID pId,
                        @Param("mId") UUID mId);

        // Recherche médicaments disponibles d'une pharmacie
        List<PharmacyMedication> findByPharmacyIdAndIsAvailableTrue(UUID pharmacyId);

        // Recherche médicaments en stock faible (< seuil)
        @Query("SELECT pm FROM PharmacyMedication pm WHERE pm.pharmacy.id = :pharmacyId AND pm.stockQuantity < :threshold")
        List<PharmacyMedication> findLowStockMedications(@Param("pharmacyId") UUID pharmacyId,
                        @Param("threshold") Integer threshold);

        @Query("SELECT COUNT(pm) FROM PharmacyMedication pm WHERE pm.pharmacy.id = :pharmacyId AND pm.stockQuantity < :threshold")
        long countLowStock(@Param("pharmacyId") UUID pharmacyId, @Param("threshold") Integer threshold);

        @Query("SELECT COUNT(pm) FROM PharmacyMedication pm WHERE pm.pharmacy.id = :pharmacyId AND pm.expiryDate < :date AND pm.expiryDate > CURRENT_DATE")
        long countExpiringSoon(@Param("pharmacyId") UUID pharmacyId, @Param("date") java.time.LocalDate date);

        // Comparaison de prix pour un médicament
        @Query("""
                        SELECT pm FROM PharmacyMedication pm
                        WHERE pm.medication.id = :medicationId
                        AND pm.isAvailable = true
                        AND pm.pharmacy.status = 'APPROVED'
                        ORDER BY pm.price ASC
                        """)
        List<PharmacyMedication> findAvailablePricesForMedication(@Param("medicationId") UUID medicationId);

        // Recherche par plage de prix
        @Query("SELECT pm FROM PharmacyMedication pm WHERE pm.pharmacy.id = :pharmacyId AND pm.price BETWEEN :minPrice AND :maxPrice")
        List<PharmacyMedication> findByPharmacyAndPriceRange(
                        @Param("pharmacyId") UUID pharmacyId,
                        @Param("minPrice") BigDecimal minPrice,
                        @Param("maxPrice") BigDecimal maxPrice);

        // Recherche globale pour les patients - JPQL portable PostgreSQL / SQL Server
        // (le tri par distance est dans GeoSearchRepository, une implémentation par base)
        @Query("""
                        SELECT pm FROM PharmacyMedication pm
                        JOIN pm.medication m
                        JOIN pm.pharmacy p
                        WHERE pm.isAvailable = true
                        AND p.status = 'APPROVED'
                        AND (
                            LOWER(m.name) LIKE LOWER(CONCAT('%', :query, '%'))
                            OR LOWER(m.genericName) LIKE LOWER(CONCAT('%', :query, '%'))
                            OR LOWER(m.symptoms) LIKE LOWER(CONCAT('%', :query, '%'))
                        )
                        AND (CAST(:therapeuticClass AS string) IS NULL
                             OR CAST(m.therapeuticClass AS string) = CAST(:therapeuticClass AS string))
                        """)
        List<PharmacyMedication> searchGlobal(
                        @Param("query") String query,
                        @Param("therapeuticClass") String therapeuticClass);
}