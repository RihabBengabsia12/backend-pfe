package tn.rihab.projectservice.messaging.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Message RabbitMQ échangé entre project-service et analyste-service.
 * Format JSON sérialisé par Jackson2JsonMessageConverter.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DossierEvent {

    private UUID   dossierId;

    /**
     * Type d'événement :
     * DOSSIER_INDEXED    → publié par project-service, consommé par analyste-service
     * DOSSIER_SUBMITTED  → publié par project-service, consommé par analyste-service
     * SCORING_COMPLETED  → publié par analyste-service, consommé par project-service
     * MATCHING_COMPLETED → publié par analyste-service, consommé par project-service
     * APO_GENERATED      → publié par analyste-service, consommé par project-service
     * AUDIT_GENERATED    → publié par analyste-service, consommé par project-service
     */
    private String eventType;

    /**
     * Payload JSON libre selon le type d'événement.
     * SCORING_COMPLETED  : {"pwinScore":72.5,"decision":"GO","scoreA":0.8,...}
     * MATCHING_COMPLETED : {"tauxCompetences":0.85,"tauxExperts":0.7}
     * APO_GENERATED      : {"apoDocxPath":"apo-generees/uuid.docx","methodoPath":"...","rapportPath":"..."}
     * AUDIT_GENERATED    : {"auditReportPath":"rapports-audit/uuid.docx"}
     */
    private String payload;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /** Pour traçabilité et déduplication */
    @Builder.Default
    private UUID correlationId = UUID.randomUUID();
}