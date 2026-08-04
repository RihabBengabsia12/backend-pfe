package tn.rihab.analysteservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.rihab.analysteservice.model.SystemAuditEntry;
import tn.rihab.analysteservice.repository.SystemAuditEntryRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemAuditService {

    private final SystemAuditEntryRepository systemAuditRepo;

    /**
     * Enregistre une modification de configuration globale.
     * @param acteur L'email ou nom de l'administrateur
     * @param action Le type d'action (ex: UPDATE_SCORING_THRESHOLDS)
     * @param details Détail de ce qui a changé
     */
    public SystemAuditEntry logSystemAction(String acteur, String action, String details) {
        SystemAuditEntry entry = SystemAuditEntry.builder()
                .acteur(acteur != null ? acteur : "system_admin")
                .action(action)
                .details(details)
                .build();
        entry = systemAuditRepo.save(entry);
        log.info("[SystemAudit] {} a effectué {} : {}", acteur, action, details);
        return entry;
    }

    /**
     * Récupère l'historique complet pour le Dashboard Admin.
     */
    public List<SystemAuditEntry> getGlobalHistory() {
        return systemAuditRepo.findAllByOrderByTimestampDesc();
    }
}
