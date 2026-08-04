package tn.rihab.analysteservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Résultat du calcul de scoring P-Win pour un dossier.
 * Stocké après chaque calcul (ScoringEngine.calculate()).
 * Une seule ligne active par dossier (recalcul = update).
 */
@Entity
@Table(name = "pwin_scores")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PwinScore {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "dossier_id", nullable = false, unique = true)
    private UUID dossierId;

    /** Score global pondéré 0-100 */
    @Column(name = "score_global")
    private Double scoreGlobal;

    /** Axe A — Faisabilité (poids 25%), valeur 0.0-1.0 avant pondération */
    @Column(name = "score_a_faisabilite")
    private Double scoreA;

    /** Axe B — Rentabilité (poids 25%) */
    @Column(name = "score_b_rentabilite")
    private Double scoreB;

    /** Axe C — Risques (poids 25%) — forcé à 0 si un risque Rédhibitoire */
    @Column(name = "score_c_risques")
    private Double scoreC;

    /** Axe D — Concurrence (poids 15%) */
    @Column(name = "score_d_concurrence")
    private Double scoreD;

    /** Axe E — Conformité (poids 10%) */
    @Column(name = "score_e_conformite")
    private Double scoreE;

    /**
     * Décision automatique calculée :
     * "GO" (score >= seuilGoFort)
     * "GO_CONDITIONNEL" (seuilGoConditionnel <= score < seuilGoFort)
     * "NO_GO" (score < seuilNoGo, ou risque Rédhibitoire)
     * "MANUAL" (seuilNoGo <= score < seuilGoConditionnel — intervention requise)
     */
    @Column(name = "decision_auto", length = 30)
    private String decisionAuto;

    /** Si NO_GO : motif principal résumé (pour affichage rapide) */
    @Column(name = "motif_nogo", length = 500)
    private String motifNogo;

    /** True si le risque C a été forcé à 0 par un risque Rédhibitoire */
    @Column(name = "risque_redhibitoire")
    @Builder.Default
    private Boolean risqueRedhibitoire = false;

    /** Nom du champ de risque qui a déclenché le Rédhibitoire (ex: "PENALITES") */
    @Column(name = "risque_redhibitoire_champ", length = 100)
    private String risqueRedhibitoireChamp;

    // ── Forçage Go (Phase 2 → MANUAL_INTERVENTION → FORCE_GO) ────────────────

    @Column(name = "force_go")
    @Builder.Default
    private Boolean forceGo = false;

    /** Justification obligatoire (100+ mots) */
    @Column(name = "force_go_justif", length = 2000)
    private String forceGoJustif;

    /** STRATEGIQUE | PARTENARIAT_A_CONSOLIDER | CLIENT_PRIORITAIRE | AUTRE */
    @Column(name = "force_go_type", length = 50)
    private String forceGoType;

    @Column(name = "force_go_by", length = 200)
    private String forceGoBy;

    @Column(name = "force_go_at")
    private LocalDateTime forceGoAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}