package tn.rihab.adminservice.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import tn.rihab.adminservice.entity.DataEvent;
import java.util.UUID;

public interface DataEventRepository extends JpaRepository<DataEvent, Long> {
    Page<DataEvent> findByActorUserId(UUID actorUserId, Pageable pageable);
    Page<DataEvent> findByEntityName(String entityName,  Pageable pageable);
    Page<DataEvent> findByAction(String action,           Pageable pageable);
}