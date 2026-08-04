package tn.rihab.projectservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.rihab.projectservice.model.entity.AuditEntry;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditEntryRepository extends JpaRepository<AuditEntry, UUID> {

    /** Historique complet trié chronologiquement — utilisé dans le rapport d'audit */
    List<AuditEntry> findByDossierIdOrderByTimestampAsc(UUID dossierId);

    /** Toutes les corrections humaines d'un dossier */
    List<AuditEntry> findByDossierIdAndAction(UUID dossierId, String action);

    /** Vérifier si un forçage Go a eu lieu */
    boolean existsByDossierIdAndAction(UUID dossierId, String action);
}