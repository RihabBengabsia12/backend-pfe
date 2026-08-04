package tn.rihab.analysteservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.rihab.analysteservice.model.IaAuditLog;

import java.util.List;
import java.util.UUID;

@Repository
public interface IaAuditLogRepository extends JpaRepository<IaAuditLog, UUID> {
    List<IaAuditLog> findByDossierIdOrderByCreatedAtDesc(UUID dossierId);
    Page<IaAuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
