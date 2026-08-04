package tn.rihab.analysteservice.repository;

import tn.rihab.analysteservice.model.MatchingResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatchingResultRepository extends JpaRepository<MatchingResult, UUID> {
    Optional<MatchingResult> findByDossierId(UUID dossierId);
}