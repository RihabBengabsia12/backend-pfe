package tn.rihab.projectservice.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tn.rihab.projectservice.config.RabbitMQConfig;
import tn.rihab.projectservice.messaging.dto.DossierEvent;
import tn.rihab.projectservice.model.DossierStatus;
import tn.rihab.projectservice.model.entity.Dossier;
import tn.rihab.projectservice.repository.DossierRepository;
import tn.rihab.projectservice.service.AuditTrailService;

/**
 * Consomme les événements publiés par analyste-service.
 *
 * Queues écoutées :
 *   q.scoring.completed     → stocker P-Win, passer en SCORING ou MANUAL_INTERVENTION
 *   q.matching.completed    → stocker résultats, passer en MATCHING
 *   q.apo.generated         → stocker URLs docs, passer en REPORT_GENERATED ou PACK_READY
 *   q.audit.generated       → stocker rapport audit, passer en ARCHIVED
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventConsumer {

    private final DossierRepository dossierRepository;
    private final AuditTrailService auditTrailService;
    private final ObjectMapper      objectMapper;
    private final org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;

    // ── Scoring terminé ────────────────────────────────────────────────────
    @RabbitListener(queues = RabbitMQConfig.Q_SCORING_COMPLETED)
    public void onScoringCompleted(DossierEvent event) {
        try {
            Dossier dossier = findDossier(event);
            JsonNode payload = objectMapper.readTree(event.getPayload());

            double pwin     = payload.path("pwinScore").asDouble(0);
            String decision = payload.path("decision").asText("MANUAL");

            dossier.setPwinScore(pwin);

            DossierStatus nouveauStatut = switch (decision) {
                case "GO"             -> DossierStatus.SCORING;
                case "GO_CONDITIONNEL"-> DossierStatus.SCORING;
                case "NO_GO"          -> DossierStatus.MANUAL_INTERVENTION;
                case "MANUAL"         -> DossierStatus.MANUAL_INTERVENTION;
                case "NO-GO"          -> DossierStatus.NO_GO_CONFIRMED;
                default               -> DossierStatus.MANUAL_INTERVENTION;
            };

            // Anti-race condition RabbitMQ : si le dossier est déjà avancé (Matching, Pack, etc.), on ne le rétrograde pas en SCORING.
            if (nouveauStatut == DossierStatus.SCORING && dossier.getStatus() != null) {
                int currentOrdinal = dossier.getStatus().ordinal();
                int scoringOrdinal = DossierStatus.SCORING.ordinal();
                if (currentOrdinal > scoringOrdinal) {
                    nouveauStatut = dossier.getStatus();
                }
            }

            // ── AJOUT : stocker le chemin du rapport No-Go si présent ──────────
            if (payload.has("nogoReportPath") && !payload.path("nogoReportPath").asText().isBlank()) {
                dossier.setNogoReportPath(payload.path("nogoReportPath").asText());
            }

            dossier.setStatus(nouveauStatut);
            dossierRepository.save(dossier);

            auditTrailService.log(dossier.getId(), "SCORING_COMPLETED",
                    "analyste-service", event.getPayload(), nouveauStatut);

            // ── ENVOI NOTIFICATION NO-GO (ROUTAGE INTELLIGENT) ──────────
            if ("NO_GO".equals(decision) || "MANUAL".equals(decision)) {
                String notifyRole = payload.path("notifyRole").asText("ROLE_DO");
                String emailCible = getEmailByRole(notifyRole); // Méthode utilitaire (voir ci-dessous)
                
                // Préparer le message pour EmailConsumer (auth-service)
                tn.rihab.projectservice.messaging.dto.EmailNotificationDTO mailDto = 
                    new tn.rihab.projectservice.messaging.dto.EmailNotificationDTO();
                mailDto.setTo(emailCible);
                mailDto.setSubject("Alerte NO-GO : Décision requise pour " + dossier.getIntituleOffre());
                mailDto.setMessage("Le projet " + dossier.getIntituleOffre() + 
                                 " a reçu un score P-Win de " + pwin + "%.\n" +
                                 "Un rapport NO-GO a été généré.\n" +
                                 "Rôle assigné (Budget) : " + notifyRole + ".\n" +
                                 "Veuillez consulter la plateforme pour forcer le GO ou confirmer l'abandon.");
                
                // Envoi vers la file d'email (que auth-service écoute)
                if (rabbitTemplate != null) {
                    rabbitTemplate.convertAndSend("mail.queue", mailDto);
                    log.info("[RabbitMQ] Email NO-GO routé vers la file 'mail.queue' pour le rôle {}", notifyRole);
                }
            }

        } catch (Exception e) {
            log.error("[RabbitMQ] Erreur SCORING_COMPLETED: {}", e.getMessage(), e);
        }
    }

    private String getEmailByRole(String role) {
        return switch (role) {
            case "ROLE_PDG" -> "pdg@st2i.com.tn";
            case "ROLE_DGA" -> "dga@st2i.com.tn";
            case "ROLE_DDA" -> "dda@st2i.com.tn";
            default -> "do@st2i.com.tn";
        };
    }

    // ── Matching terminé ───────────────────────────────────────────────────
    @RabbitListener(queues = RabbitMQConfig.Q_MATCHING_COMPLETED)
    public void onMatchingCompleted(DossierEvent event) {
        log.info("[RabbitMQ] Reçu MATCHING_COMPLETED pour dossier {}", event.getDossierId());
        try {
            Dossier dossier = findDossier(event);
            dossier.setStatus(DossierStatus.MATCHING);
            dossierRepository.save(dossier);

            auditTrailService.log(dossier.getId(), "MATCHING_COMPLETED",
                    "analyste-service", event.getPayload(), DossierStatus.MATCHING);

        } catch (Exception e) {
            log.error("[RabbitMQ] Erreur traitement MATCHING_COMPLETED: {}", e.getMessage(), e);
        }
    }

    // ── APO et documents générés ───────────────────────────────────────────
    /**
     * Reçu quand analyste-service a terminé la génération de :
     *   - L'APO DOCX remplie (56 placeholders)
     *   - La méthodologie DOCX (template méthodologie)
     *   - Le rapport général DOCX (template rapport résultat)
     * → Passe en REPORT_GENERATED, puis génère le pack ZIP → PACK_READY.
     */
    @RabbitListener(queues = RabbitMQConfig.Q_APO_GENERATED)
    public void onApoGenerated(DossierEvent event) {
        log.info("[RabbitMQ] Reçu APO_GENERATED pour dossier {}", event.getDossierId());
        try {
            Dossier dossier = findDossier(event);
            JsonNode payload = objectMapper.readTree(event.getPayload());

            // Stocker les chemins MinIO des documents générés
            if (payload.has("apoDocxPath"))
                dossier.setApoDocxPath(payload.path("apoDocxPath").asText());
            if (payload.has("methodoPath"))
                dossier.setMethodoDocxPath(payload.path("methodoPath").asText());
            if (payload.has("rapportPath"))
                dossier.setRapportPath(payload.path("rapportPath").asText());
            if (payload.has("packZipPath")) {
                dossier.setPackZipPath(payload.path("packZipPath").asText());
                dossier.setStatus(DossierStatus.PACK_READY);
            } else {
                dossier.setStatus(DossierStatus.REPORT_GENERATED);
            }

            dossierRepository.save(dossier);

            auditTrailService.log(dossier.getId(), "APO_GENERATED",
                    "analyste-service", event.getPayload(), dossier.getStatus());

        } catch (Exception e) {
            log.error("[RabbitMQ] Erreur traitement APO_GENERATED: {}", e.getMessage(), e);
        }
    }

    // ── Rapport d'audit généré ─────────────────────────────────────────────
    @RabbitListener(queues = RabbitMQConfig.Q_AUDIT_GENERATED)
    public void onAuditGenerated(DossierEvent event) {
        log.info("[RabbitMQ] Reçu AUDIT_GENERATED pour dossier {}", event.getDossierId());
        try {
            Dossier dossier = findDossier(event);
            JsonNode payload = objectMapper.readTree(event.getPayload());

            String auditReportPath = payload.path("auditReportPath").asText("");
            if (auditReportPath.isBlank()) {
                log.error("[RabbitMQ] AUDIT_GENERATED ignoré pour {} : chemin du rapport absent", event.getDossierId());
                return;
            }
            dossier.setAuditReportPath(auditReportPath);

            dossier.setStatus(DossierStatus.ARCHIVED);
            dossierRepository.save(dossier);

            auditTrailService.log(dossier.getId(), "ARCHIVED",
                    "analyste-service", event.getPayload(), DossierStatus.ARCHIVED);

        } catch (Exception e) {
            log.error("[RabbitMQ] Erreur traitement AUDIT_GENERATED: {}", e.getMessage(), e);
        }
    }

    // ── Utilitaire ─────────────────────────────────────────────────────────
    private Dossier findDossier(DossierEvent event) {
        return dossierRepository.findById(event.getDossierId())
                .orElseThrow(() -> new IllegalStateException(
                        "Dossier introuvable: " + event.getDossierId()));
    }
    @RabbitListener(queues = RabbitMQConfig.Q_FORCE_COMPATIBLE)
    public void onForceCompatible(DossierEvent event) {
        log.info("[RabbitMQ] Reçu FORCE_COMPATIBLE pour dossier {}", event.getDossierId());
        try {
            JsonNode payload = objectMapper.readTree(event.getPayload());
            String forcedBy = payload.path("forcedBy").asText("Inconnu");

            auditTrailService.log(
                    event.getDossierId(),
                    "FORCE_COMPATIBLE",
                    forcedBy,
                    event.getPayload(),
                    null
            );
            
            // ── ENVOI NOTIFICATION AU ANALYSTE ──────────
            Dossier dossier = findDossier(event);
            dossier.setAnalystNotified(true);
            dossierRepository.save(dossier);

            tn.rihab.projectservice.messaging.dto.EmailNotificationDTO mailDto = 
                new tn.rihab.projectservice.messaging.dto.EmailNotificationDTO();
            mailDto.setTo("analyste@st2i.com.tn");
            mailDto.setSubject("Décision Manager : Projet Forcé GO (" + dossier.getIntituleOffre() + ")");
            mailDto.setMessage("Le manager (" + forcedBy + ") a décidé de FORCER LE GO pour le projet : " + dossier.getIntituleOffre() + ".\n" +
                             "Le projet passe en Phase 3. Veuillez continuer le traitement sur la plateforme.");
            
            if (rabbitTemplate != null) {
                rabbitTemplate.convertAndSend("mail.queue", mailDto);
                log.info("[RabbitMQ] Email envoyé à l'analyste pour le Forçage GO du dossier {}", event.getDossierId());
            }

        } catch (Exception e) {
            log.error("[RabbitMQ] Erreur traitement FORCE_COMPATIBLE: {}", e.getMessage(), e);
        }
    }
}
