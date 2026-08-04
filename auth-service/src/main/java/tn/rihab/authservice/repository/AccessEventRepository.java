package tn.rihab.authservice.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import tn.rihab.authservice.entity.AccessEvent;
import java.util.UUID;

// ── Remplace ton AccessEventRepository existant par celui-ci ─────────────────
public interface AccessEventRepository extends JpaRepository<AccessEvent, UUID> {

    Page<AccessEvent> findByEmailAttempted(String email, Pageable pageable);

    Page<AccessEvent> findByEventType(String eventType, Pageable pageable);

    Page<AccessEvent> findByEmailAttemptedAndEventType(
            String email, String eventType, Pageable pageable);
}