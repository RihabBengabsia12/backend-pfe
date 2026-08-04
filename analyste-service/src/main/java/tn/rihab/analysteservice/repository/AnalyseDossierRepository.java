package tn.rihab.analysteservice.repository;

import tn.rihab.analysteservice.model.AnalyseDossier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** CRUD AnalyseDossier — données Phase 2 (1 ligne par dossier). */
@Repository
public interface AnalyseDossierRepository extends JpaRepository<AnalyseDossier, UUID> {
    Optional<AnalyseDossier> findByDossierId(UUID dossierId);
    boolean existsByDossierId(UUID dossierId);
}