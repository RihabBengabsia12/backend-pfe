package tn.rihab.analysteservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Données Phase 2 — Analyse approfondie.
 * dossierId référence le Dossier de project-service (pas de FK JPA cross-service,
 * juste un UUID récupéré via ProjectServiceClient).
 *
 * Contient :
 * - 16 champs extraits/évalués (dates, financement, notation, caution)
 * - 10 champs risques (niveau + justification)
 * - 2 champs internes (capacité délai, transmission)
 */
@Entity
@Table(name = "analyse_dossier") // Harmonisé au singulier pour correspondre à votre BDD
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyseDossier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Référence vers Dossier.id dans project-service */
    @Column(name = "dossier_id", nullable = false, unique = true)
    private UUID dossierId;

    // ════════════════════════════════════════════════════════════
    // Dates & Délais
    // ════════════════════════════════════════════════════════════

    /** [[DATE_LIMITE_SOUMISSION]] — répétition contextuelle, sync avec DT_LIM_SOUM */
    @Column(name = "date_limite_soumission")
    private LocalDate dateLimiteSoumission;

    /** [[DELAI_PREP_SUF]] — "Oui"/"Non", calculé puis modifiable */
    @Column(name = "delai_prep_suf", length = 10)
    private String delaiPrepSuf;

    /** [[JUSTIF_DELAI_PREP]] — obligatoire si delaiPrepSuf = "Non" */
    @Column(name = "justif_delai_prep", length = 1000)
    private String justifDelaiPrep;

    /** [[DELAI_GLOBAL_MOIS]] — durée totale de la mission en mois */
    @Column(name = "delai_global_mois")
    private Integer delaiGlobalMois;

    /** [[DATE_LIMITE_QUESTIONS]] — deadline clarifications client */
    @Column(name = "date_limite_questions")
    private LocalDate dateLimiteQuestions;

    // ════════════════════════════════════════════════════════════
    // Notation / Pondérations
    // ════════════════════════════════════════════════════════════

    /** [[NOTE_MINIMALE]] — seuil éliminatoire technique, ex "70/100" */
    @Column(name = "note_minimale", length = 50)
    private String noteMinimale;

    /** [[PON_TECH]] — pondération technique en % (doit + PON_FIN = 100) */
    @Column(name = "pon_tech")
    private Double ponTech;

    /** [[PON_FIN]] — pondération financière en % */
    @Column(name = "pon_fin")
    private Double ponFin;

    // ════════════════════════════════════════════════════════════
    // Financement local
    // ════════════════════════════════════════════════════════════

    /** [[FIN_LOCAL_OUI_NON]] — "Oui"/"Non" */
    @Column(name = "fin_local_oui_non", length = 10)
    private String finLocalOuiNon;

    /** [[FINA_LOCAL_DETAILS]] — détails si Oui */
    @Column(name = "fina_local_details", length = 1000)
    private String finaLocalDetails;

    // ════════════════════════════════════════════════════════════
    // Caution de soumission
    // ════════════════════════════════════════════════════════════

    /** [[CAUTION_MONTANT]] */
    @Column(name = "caution_montant", length = 100)
    private String cautionMontant;

    /** [[CAUTION_MONNAIE]] */
    @Column(name = "caution_monnaie", length = 20)
    private String cautionMonnaie;

    /** [[CAUTION_DUREE]] — ex "90 jours" */
    @Column(name = "caution_duree", length = 50)
    private String cautionDuree;

    /** [[BANQUE_LOCALE_EXIGEE]] — "Oui"/"Non" */
    @Column(name = "banque_locale_exigee", length = 10)
    private String banqueLocaleExigee;

    // ════════════════════════════════════════════════════════════
    // 10 Risques — niveau (Faible/Modéré/Élevé/Rédhibitoire) + justification
    // Stockés au format "NIVEAU||justification" pour simplicité,
    // exposés séparément via DTO côté API.
    // ════════════════════════════════════════════════════════════

    /** [[RISQUE_PAYS_SECURITE]] */
    @Column(name = "risque_pays_securite", length = 1000)
    private String risquePaysSecurite;

    /** [[RISQUES_FINANCIERS]] */
    @Column(name = "risques_financiers", length = 1000)
    private String risquesFinanciers;

    /** [[PENALITES]] */
    @Column(name = "penalites", length = 1000)
    private String penalites;

    /** [[EXIGENCES_TDR_INACCEPTABLES]] */
    @Column(name = "exigences_tdr_inacceptables", length = 1000)
    private String exigencesTdrInacceptables;

    /** [[GARANTIES_ASSURANCES_ELEVEES]] */
    @Column(name = "garanties_assurances_elevees", length = 1000)
    private String garantiesAssurancesElevees;

    /** [[TAILLE_DISPERSION]] */
    @Column(name = "taille_dispersion", length = 1000)
    private String tailleDispersion;

    /** [[FRAIS_DIVERS_ELEVES]] */
    @Column(name = "frais_divers_eleves", length = 1000)
    private String fraisDiversEleves;

    /** [[BUDGET_FAIBLE_HM_LIMITES]] — pré-rempli depuis TJM_IMPLICITE */
    @Column(name = "budget_faible_hm_limites", length = 1000)
    private String budgetFaibleHmLimites;

    /** [[PARTICIPATION_LOCALE_EXCESSIVE]] */
    @Column(name = "participation_locale_excessive", length = 1000)
    private String participationLocaleExcessive;

    /** [[FISCALITE_NON_MAITRISEE]] */
    @Column(name = "fiscalite_non_maitrisee", length = 1000)
    private String fiscaliteNonMaitrisee;

    // ════════════════════════════════════════════════════════════
    // Champs internes Egis (saisie manuelle)
    // ════════════════════════════════════════════════════════════

    /** [[CAPACITE_DELAI]] — "Oui"/"Non", saisie RO obligatoire */
    @Column(name = "capacite_delai", length = 10)
    private String capaciteDelai;

    /** [[JUSTIF_CAPACITE_DELAI]] — obligatoire si capaciteDelai = "Non" */
    @Column(name = "justif_capacite_delai", length = 1000)
    private String justifCapaciteDelai;

    /** [[TRANSMISSION]] — date transmission DO/DDA */
    @Column(name = "transmission")
    private LocalDate transmission;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ════════════════════════════════════════════════════════════
// Alignement & Concurrence (Ajouté pour le calcul de l'Axe D)
// ════════════════════════════════════════════════════════════

    /** [[SHORTLIST_EQUILIBREE]] — "Oui"/"Non"/"NA", configuré via l'IHM Angular */
    @Column(name = "shortlist_equilibree", length = 10)
    private String shortlistEquilibree;
}