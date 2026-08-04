package tn.rihab.analysteservice.repository;

import tn.rihab.analysteservice.model.ScoringConfig; // <--- CORRIGÉ ICI
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository ScoringConfig — singleton applicatif.
 * findFirst() utilisé partout car une seule ligne existe
 * (initialisée par ScoringConfigLoader au démarrage).
 */
@Repository
public interface ScoringConfigRepository extends JpaRepository<ScoringConfig, UUID> {

    default ScoringConfig findCurrent() {
        return findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Aucune ScoringConfig trouvée — vérifier ScoringConfigLoader"));
    }
}