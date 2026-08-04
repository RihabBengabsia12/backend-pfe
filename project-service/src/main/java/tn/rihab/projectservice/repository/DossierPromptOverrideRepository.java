package tn.rihab.projectservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.rihab.projectservice.model.entity.DossierPromptOverride;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DossierPromptOverrideRepository extends JpaRepository<DossierPromptOverride, Long> {
    List<DossierPromptOverride> findByDossierId(UUID dossierId);
    Optional<DossierPromptOverride> findByDossierIdAndPromptFilename(UUID dossierId, String promptFilename);
}
