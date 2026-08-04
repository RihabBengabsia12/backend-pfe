package tn.rihab.projectservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.rihab.projectservice.model.entity.ExtractionMetadata;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExtractionMetadataRepository extends JpaRepository<ExtractionMetadata, UUID> {

    Optional<ExtractionMetadata> findByDossierIdAndFieldName(UUID dossierId, String fieldName);

    List<ExtractionMetadata> findByDossierIdAndPhase(UUID dossierId, String phase);

    List<ExtractionMetadata> findByDossierId(UUID dossierId);

    // ── AJOUTÉ : Pour le tri des champs dans l'interface Angular (P1)
    List<ExtractionMetadata> findByDossierIdOrderByFieldNameAsc(UUID dossierId);

    /** Champs corrigés manuellement — pour le rapport d'audit */
    List<ExtractionMetadata> findByDossierIdAndHumanModifiedTrue(UUID dossierId);

    /** Champs à confiance faible (pour le panneau d'alertes Angular) */
    List<ExtractionMetadata> findByDossierIdAndConfianceLessThan(UUID dossierId, Double threshold);
}