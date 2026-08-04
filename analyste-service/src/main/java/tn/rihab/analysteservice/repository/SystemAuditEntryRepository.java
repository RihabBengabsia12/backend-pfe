package tn.rihab.analysteservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.rihab.analysteservice.model.SystemAuditEntry;

import java.util.List;

@Repository
public interface SystemAuditEntryRepository extends JpaRepository<SystemAuditEntry, Long> {

    // Retourne l'historique global trié par date décroissante (le plus récent en premier)
    List<SystemAuditEntry> findAllByOrderByTimestampDesc();
}
