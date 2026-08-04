package tn.rihab.projectservice.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Métadonnées d'extraction par champ APO.
 * Trace la valeur Claude originale, la valeur finale, la confiance
 * et si un humain a corrigé. Utilisé pour le rapport d'audit Phase 6.
 */
@Entity
@Table(name = "extraction_metadata",
        uniqueConstraints = @UniqueConstraint(columnNames = {"dossier_id","field_name"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExtractionMetadata {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "dossier_id", nullable = false)
    private UUID dossierId;

    /**
     * Nom du placeholder sans crochets.
     * Ex: "PAYS", "DT_LIM_SOUM", "BUDGET_GLOBAL"
     */
    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    /** Valeur brute retournée par Claude */
    @Column(name = "valeur_claude", length = 2000)
    private String valeurClaude;

    /** Valeur après validation/correction humaine */
    @Column(name = "valeur_finale", length = 2000)
    private String valeurFinale;

    /**
     * Score de confiance : 0.0–1.0
     * >=0.85 = vert | 0.60-0.84 = ambre | <0.60 = rouge
     */
    @Column
    private Double confiance;

    /**
     * Source de la valeur finale :
     * "claude_extraction" | "human_correction" | "claude_reextraction" | "computed"
     */
    @Column(length = 50) @Builder.Default
    private String source = "claude_extraction";

    /** True si l'utilisateur a modifié la valeur Claude */
    @Column(name = "human_modified") @Builder.Default
    private Boolean humanModified = false;

    /** Citation source affichée en italique sous le champ dans Angular */
    @Column(name = "source_extrait", length = 300)
    private String sourceExtrait;

    /** Nb ré-extractions (max 2) */
    @Column(name = "reextraction_count") @Builder.Default
    private Integer reextractionCount = 0;

    /** "P1" | "P2" */
    @Column(length = 5) @Builder.Default
    private String phase = "P1";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}