package tn.rihab.projectservice.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Token de validation hiérarchique envoyé par email.
 * Usage unique, expiration 48h.
 * Rôles : DO | DDA | DGA | PDG
 * Statuts : PENDING | APPROVED | REJECTED | EXPIRED
 */
@Entity
@Table(name = "validation_tokens")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ValidationToken {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false, length = 200)
    private String token;

    @Column(name = "dossier_id", nullable = false)
    private UUID dossierId;

    /** DO | DDA | DGA | PDG */
    @Column(name = "validateur_role", nullable = false, length = 20)
    private String validateurRole;

    @Column(name = "validateur_email", nullable = false, length = 200)
    private String validateurEmail;

    @Column(name = "validateur_nom", length = 200)
    private String validateurNom;

    /** PENDING | APPROVED | REJECTED | EXPIRED */
    @Column(nullable = false, length = 20) @Builder.Default
    private String status = "PENDING";

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "action_at")
    private LocalDateTime actionAt;

    /** Obligatoire si REJECTED */
    @Column(length = 1000)
    private String commentaire;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** Anti double-clic */
    @Column @Builder.Default
    private Boolean used = false;
}