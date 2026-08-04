package tn.rihab.projectservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.rihab.projectservice.model.DossierStatus;
import tn.rihab.projectservice.model.entity.AuditEntry;
import tn.rihab.projectservice.repository.AuditEntryRepository;

import java.util.List;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Slf4j
public class AuditTrailService {

    private final AuditEntryRepository auditRepo;


    public void log(UUID dossierId, String action, String acteur,
                    String detail, DossierStatus newStatus) {
        AuditEntry entry = AuditEntry.builder()
                .dossierId(dossierId)
                .action(action)
                .acteur(acteur != null ? acteur : "system")
                .detail(detail)
                .statusApres(newStatus != null ? newStatus.name() : null)
                .build();

        auditRepo.save(entry);
        log.debug("[Audit] {} — {} — {}", dossierId, action, detail);
    }

    /** Historique complet d'un dossier, trié chronologiquement. */
    public List<AuditEntry> getHistory(UUID dossierId) {
        return auditRepo.findByDossierIdOrderByTimestampAsc(dossierId);
    }

    /** Vérifie si un forçage Go a eu lieu sur ce dossier. */
    public boolean hasForceGo(UUID dossierId) {
        return auditRepo.existsByDossierIdAndAction(dossierId, "FORCE_GO");
    }


}