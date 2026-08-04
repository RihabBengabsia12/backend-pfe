package tn.rihab.analysteservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Dictionnaire complet de l'APO pour un dossier.
 * Stocke les 56 placeholders réglementaires + les 9 champs recommandés
 * sous forme de Map<String, ChampApo> sérialisée en JSONB.
 *
 * Clé   = nom du placeholder sans crochets (ex "PAYS", "PWIN_SCORE")
 * Valeur = ChampApo { valeur, statut, source }
 *
 * statut ∈ { AUTO, CLAUDE, MANUAL, CALCULATED, EMPTY }
 *   AUTO       → repris tel quel depuis Phase 1 (project-service)
 *   CLAUDE     → généré/extrait par Claude (Phase 2/3/4)
 *   MANUAL     → saisie humaine obligatoire
 *   CALCULATED → calculé automatiquement (TJM, PWIN_SCORE, GAP_REFS...)
 *   EMPTY      → non rempli (affiché en orange dans Angular)
 */
@Entity
@Table(name = "apo_data")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApoData {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "dossier_id", nullable = false, unique = true)
    private UUID dossierId;

    /** Map complète des champs APO sérialisée en JSONB (PostgreSQL) */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "champs", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, ChampApo> champs = new HashMap<>();

    /** Pourcentage de complétude (0-100), recalculé à chaque update */
    @Column(name = "completeness")
    private Double completeness;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Représentation d'un champ APO individuel.
     * Sérialisé en JSON dans la colonne champs (jsonb).
     */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ChampApo {
        private String valeur;
        /** AUTO | CLAUDE | MANUAL | CALCULATED | EMPTY */
        private String statut;
        /** "project-service" | "claude-api" | "analyste-referentiel" | "user" */
        private String source;
    }

    // ── Liste de référence des 56 placeholders réglementaires ────────────────
    public static final String[] PLACEHOLDERS_REGLEMENTAIRES = {
            "PAYS","INTITULE_OFFRE","NUMERO_REFERENCE","CLIENT","LANGUE",
            "DT_LIM_SOUM","ARRIVEE_BO","TRANSMISSION","DATE_LIMITE_QUESTIONS","DATE_LIMITE_SOUMISSION",
            "BAILLEURS","BUDGET_GLOBAL","FIN_LOCAL_OUI_NON","FINA_LOCAL_DETAILS",
            "HOMMES_MOIS","BUDGET_INTERNE","SOURCE_BUDGET_INTERNE",
            "SHORTLIST","SHORTLIST_EQUILIBREE","JUSTIF_SHORTLIST","ANALYSE_CONCURRENCE",
            "MODE_NOTATION","NOTE_MINIMALE","PON_TECH","PON_FIN",
            "PARTENAIRES","CHEF_DE_FILE","ROLES_REPARTITION",
            "CAUTION_MONNAIE","CAUTION_MONTANT","CAUTION_DUREE","BANQUE_LOCALE_EXIGEE",
            "VISITE_OBL","VISITE_DATE","CONF_OBL","CONF_DATE","LISTE_CLARIFICATIONS",
            "RISQUE_PAYS_SECURITE","RISQUES_FINANCIERS","PENALITES","EXIGENCES_TDR_INACCEPTABLES",
            "GARANTIES_ASSURANCES_ELEVEES","TAILLE_DISPERSION","FRAIS_DIVERS_ELEVES",
            "BUDGET_FAIBLE_HM_LIMITES","PARTICIPATION_LOCALE_EXCESSIVE","FISCALITE_NON_MAITRISEE",
            "RESUME_CONTEXTE_OBJECTIFS","POINTS_CRITIQUES","RECOMMANDATION_GO_NOGO","ARGUMENTAIRE_GO_NOGO",
            "PLAN_ACTION","DELAI_GLOBAL_MOIS","DELAI_PREP_SUF","JUSTIF_DELAI_PREP",
            "CAPACITE_DELAI","JUSTIF_CAPACITE_DELAI"
    };

    // ── Liste des 9 champs recommandés (extension ProjectIQ) ──────────────────
    public static final String[] PLACEHOLDERS_NOUVEAUX = {
            "PWIN_SCORE","TJM_IMPLICITE",
            "REFS_EXIGEES","GAP_REFS",
            "EXPERTS_REQUIS","TAUX_COUVERTURE_EXPERTS",
            "RELATION_CLIENT","QUALIFS_EXIGEES","ALIGNEMENT_STRATEGIQUE"
    };
}