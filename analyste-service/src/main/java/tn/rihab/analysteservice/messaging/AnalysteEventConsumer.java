package  tn.rihab.analysteservice.messaging;

import tn.rihab.analysteservice.config.RabbitMQConfig;
import tn.rihab.analysteservice.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Consomme les événements publiés par project-service.
 *
 * dossier.indexed   → déclenche le pipeline complet Phase 2→3→4
 *                     (analyse + scoring + matching + APO + génération docs)
 * dossier.submitted → déclenche la génération du rapport d'audit Phase 6
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnalysteEventConsumer {

    private final AnalyseDeepService     analyseDeepService;
    private final AuditGenerationService auditGenerationService;

    /**
     * Déclenché quand project-service publie DossierIndexed.
     * Lance séquentiellement Phase 2 → scoring → matching → APO → docs.
     *
     * Payload : { "dossierId": "uuid", "eventType": "DOSSIER_INDEXED" }
     */
    @RabbitListener(queues = RabbitMQConfig.Q_DOSSIER_INDEXED)
    public void onDossierIndexed(Map<String, Object> event) {
        String raw = event.get("dossierId").toString();
        UUID dossierId = UUID.fromString(raw);
        log.info("[Consumer] DOSSIER_INDEXED reçu → lancement pipeline Phase 2-4 pour {}", dossierId);
        try {
            analyseDeepService.runFullPipeline(dossierId);
        } catch (Exception e) {
            log.error("[Consumer] Erreur pipeline dossier {} : {}", dossierId, e.getMessage(), e);
        }
    }

    /**
     * Déclenché quand project-service publie DossierSubmitted.
     * Lance la génération du rapport d'audit Phase 6.
     */
    @RabbitListener(queues = RabbitMQConfig.Q_DOSSIER_SUBMITTED)
    public void onDossierSubmitted(Map<String, Object> event) {
        String raw = event.get("dossierId").toString();
        UUID dossierId = UUID.fromString(raw);
        log.info("[Consumer] DOSSIER_SUBMITTED reçu → génération rapport audit pour {}", dossierId);
        try {
            auditGenerationService.generateAuditReport(dossierId);
        } catch (Exception e) {
            log.error("[Consumer] Erreur audit dossier {} : {}", dossierId, e.getMessage(), e);
        }
    }
}