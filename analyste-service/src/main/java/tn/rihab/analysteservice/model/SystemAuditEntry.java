package tn.rihab.analysteservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Traçabilité Globale (Niveau Système) : Historise toutes les modifications
 * apportées aux paramètres globaux par les administrateurs (ex: seuils de scoring,
 * mots du dictionnaire, taux journaliers).
 */
@Entity
@Table(name = "system_audit_trail")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SystemAuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Le nom ou l'email de l'administrateur (ex: admin@st2i.com.tn) */
    @Column(name = "acteur", nullable = false)
    private String acteur;

    /** Type d'action (ex: UPDATE_SCORING_THRESHOLDS, ADD_DLP_WORD) */
    @Column(name = "action", nullable = false)
    private String action;

    /** Détail lisible de ce qui a changé (ex: "seuil_no_go modifié de 20 à 25") */
    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @CreationTimestamp
    @Column(name = "timestamp", updatable = false)
    private LocalDateTime timestamp;
}
