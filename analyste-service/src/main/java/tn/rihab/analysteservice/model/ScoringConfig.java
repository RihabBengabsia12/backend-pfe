package tn.rihab.analysteservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Configuration globale du moteur de scoring P-Win.
 * Ligne unique (singleton applicatif), modifiable via
 * GET/PUT /api/config/scoring sans redémarrage.
 */
@Entity
@Table(name = "scoring_config")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ScoringConfig {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // ── Seuils de décision (en %, 0-100) ─────────────────────────────────────

    /** Score < seuilNoGo → No-Go automatique recommandé */
    @Column(name = "seuil_no_go") @Builder.Default
    private Double seuilNoGo = 20.0;

    /** seuilNoGo <= score < seuilGoConditionnel → MANUAL_INTERVENTION */
    @Column(name = "seuil_go_conditionnel") @Builder.Default
    private Double seuilGoConditionnel = 40.0;

    /** score >= seuilGoFort → Go fort, priorité élevée */
    @Column(name = "seuil_go_fort") @Builder.Default
    private Double seuilGoFort = 70.0;

    // ── Pondérations des 5 axes (doivent sommer à 1.0) ───────────────────────

    @Column(name = "poids_a_faisabilite") @Builder.Default
    private Double poidsA_faisabilite = 0.25;

    @Column(name = "poids_b_rentabilite") @Builder.Default
    private Double poidsB_rentabilite = 0.25;

    @Column(name = "poids_c_risques") @Builder.Default
    private Double poidsC_risques = 0.25;

    @Column(name = "poids_d_concurrence") @Builder.Default
    private Double poidsD_concurrence = 0.15;

    @Column(name = "poids_e_conformite") @Builder.Default
    private Double poidsE_conformite = 0.10;

    // ── TJM Egis (€/jour) — utilisés par AxeRentabiliteCalculator ────────────

    @Column(name = "tjm_min_egis") @Builder.Default
    private Double tjmMinEgis = 350.0;

    @Column(name = "tjm_max_egis") @Builder.Default
    private Double tjmMaxEgis = 3000.0;

    // ── Seuils de compatibilité Phase 3 (matching) ───────────────────────────

    @Column(name = "seuil_compat_competences") @Builder.Default
    private Double seuilCompatCompetences = 0.50;

    @Column(name = "seuil_compat_experts") @Builder.Default
    private Double seuilCompatExperts = 0.40;
}