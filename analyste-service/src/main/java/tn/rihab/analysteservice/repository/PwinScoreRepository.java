package tn.rihab.analysteservice.repository;

import tn.rihab.analysteservice.model.PwinScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** CRUD PwinScore — 1 ligne par dossier, mise à jour à chaque recalcul. */
@Repository
public interface PwinScoreRepository extends JpaRepository<PwinScore, UUID> {
    Optional<PwinScore> findByDossierId(UUID dossierId);
}