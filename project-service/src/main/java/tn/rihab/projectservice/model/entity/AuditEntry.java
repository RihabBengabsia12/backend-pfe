package tn.rihab.projectservice.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Table d'audit append-only.
 * Jamais de UPDATE ni DELETE sur cette table.
 * Actions loguées : UPLOADED, PARSING_STARTED, PARSING_COMPLETED,
 * FIELD_VALIDATED, FIELD_CORRECTED, FIELD_REEXTRACTED, INDEXED,
 * SCORING_COMPLETED, NO_GO_RECOMMENDED, FORCE_GO, NO_GO_CONFIRMED,
 * MATCHING_COMPLETED, APO_GENERATED, NOGO_REPORT_GENERATED,
 * METHODO_GENERATED, RAPPORT_GENERATED, PACK_READY,
 * VALIDATION_SENT, VALIDATOR_APPROVED, VALIDATOR_REJECTED,
 * SUBMITTED, AUDIT_GENERATED, ARCHIVED.
 */
@Entity
@Table(name = "audit_entries")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditEntry {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "dossier_id", nullable = false)
    private UUID dossierId;

    /** Type d'action (voir javadoc de la classe) */
    @Column(nullable = false, length = 100)
    private String action;

    /** Utilisateur ou service ayant effectué l'action */
    @Column(length = 200)
    private String acteur;

    /**
     * Détails JSON libres.
     * Ex FIELD_CORRECTED : {"field":"PAYS","old":"Morocco","new":"Maroc"}
     * Ex FORCE_GO        : {"justification":"...","pwin":18.5,"type":"STRATEGIQUE"}
     * Ex VALIDATOR_*     : {"role":"DO","email":"do@egis.fr","commentaire":""}
     */
    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(name = "status_avant",  length = 50) private String statusAvant;
    @Column(name = "status_apres",  length = 50) private String statusApres;

    @CreationTimestamp
    @Column(name = "timestamp", updatable = false, nullable = false)
    private LocalDateTime timestamp;
}