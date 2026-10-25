package com.app.easypharma_backend.domain.search.geo;

import com.app.easypharma_backend.domain.medication.entity.PharmacyMedication;
import com.app.easypharma_backend.domain.pharmacy.entity.Pharmacy;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Charge les entités d'une recherche géographique avec leurs relations, en une requête par lot
 * (JOIN FETCH), dans l'ordre des identifiants reçus.
 * <p>
 * Les requêtes spatiales natives ne renvoient que les identifiants triés par distance : renvoyer
 * directement les entités laissait Hibernate charger le propriétaire de chaque pharmacie
 * (et le médicament et la pharmacie de chaque offre) par une requête séparée, soit un N+1
 * (119 requêtes pour 113 pharmacies, 490 pour 238 offres).
 */
@Component
public class GeoEntityLoader {

    /** SQL Server accepte au plus 2 100 paramètres par requête : on charge par lots. */
    private static final int BATCH = 1000;

    private final EntityManager em;

    public GeoEntityLoader(EntityManager em) {
        this.em = em;
    }

    public List<Pharmacy> pharmacies(List<UUID> orderedIds) {
        return load(orderedIds, Pharmacy::getId, """
                SELECT p FROM Pharmacy p JOIN FETCH p.user WHERE p.id IN :ids
                """, Pharmacy.class);
    }

    public List<PharmacyMedication> offers(List<UUID> orderedIds) {
        return load(orderedIds, PharmacyMedication::getId, """
                SELECT pm FROM PharmacyMedication pm
                JOIN FETCH pm.medication
                JOIN FETCH pm.pharmacy p
                JOIN FETCH p.user
                WHERE pm.id IN :ids
                """, PharmacyMedication.class);
    }

    private <T> List<T> load(List<UUID> orderedIds, Function<T, UUID> idOf, String jpql, Class<T> type) {
        Map<UUID, T> byId = new HashMap<>();
        for (int from = 0; from < orderedIds.size(); from += BATCH) {
            List<UUID> chunk = orderedIds.subList(from, Math.min(from + BATCH, orderedIds.size()));
            em.createQuery(jpql, type).setParameter("ids", chunk).getResultList()
                    .forEach(entity -> byId.put(idOf.apply(entity), entity));
        }
        List<T> ordered = new ArrayList<>(orderedIds.size());
        for (UUID id : orderedIds) {
            T entity = byId.get(id);
            if (entity != null) {
                ordered.add(entity);
            }
        }
        return ordered;
    }
}
