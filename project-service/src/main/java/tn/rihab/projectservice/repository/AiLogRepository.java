package tn.rihab.projectservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.rihab.projectservice.model.entity.AiLog;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiLogRepository extends JpaRepository<AiLog, UUID> {
    List<AiLog> findAllByOrderByCreatedAtDesc();
    List<AiLog> findByDossierIdOrderByCreatedAtDesc(UUID dossierId);
}
