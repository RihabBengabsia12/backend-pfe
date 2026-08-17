package tn.rihab.analysteservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Résultat complet du matching Phase 3.
 * Combine les sorties des 4 matchers : Compétences, Références, Experts, Client.
 * Les champs JSON sont consommés directement par l'interface Angular
 * (matching-page, matrice-page) et par ApoAssemblyService (Phase 4).
 */
@Entity
@Table(name = "matching_result") // Harmonisé au singulier pour correspondre à votre BDD
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchingResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "dossier_id", nullable = false, unique = true)
    private UUID dossierId;

    // ── CompetencesMatcher ─────────────────────────────────────────────────
    /** 0.0–1.0, alimente l'Axe A du scoring */
    @Column(name = "taux_couverture_competences")
    private Double tauxCouvertureCompetences;

    /**
     * JSON : [{"domaine":"Eau","requis":true,"niveauEgis":"EXPERT","couvert":true}, ...]
     */
    @Column(name = "competences_detail", columnDefinition = "TEXT")
    private String competencesDetail;

    // ── ReferencesMatcher ──────────────────────────────────────────────────
    /** [[REFS_EXIGEES]] — JSON des exigences extraites de la DP */
    @Column(name = "refs_exigees", columnDefinition = "TEXT")
    private String refsExigees;

    /**
     * [[GAP_REFS]] — JSON :
     * [{"exigence":"3 missions eau Afrique >2M€","reference":"Mission Sénégal 2022",
     * "couvert":"OUI|NON|PARTIEL"}, ...]
     */
    @Column(name = "gap_refs", columnDefinition = "TEXT")
    private String gapRefs;

    // ── ExpertsMatcher ─────────────────────────────────────────────────────
    /** [[EXPERTS_REQUIS]] — JSON des profils extraits du TDR */
    @Column(name = "experts_requis", columnDefinition = "TEXT")
    private String expertsRequis;

    /** [[TAUX_COUVERTURE_EXPERTS]] — 0.0–1.0 */
    @Column(name = "taux_couverture_experts")
    private Double tauxCouvertureExperts;

    /** JSON : [{"profil":"Chef de mission hydraulique","expert":"Nom","disponible":true}, ...] */
    @Column(name = "experts_detail", columnDefinition = "TEXT")
    private String expertsDetail;

    // ── ClientMatcher ──────────────────────────────────────────────────────
    /** [[RELATION_CLIENT]] — niveau 1 (premier contact) à 5 (partenaire stratégique) */
    @Column(name = "relation_client_niveau")
    private Integer relationClientNiveau;

    @Column(name = "relation_client_nb_missions")
    private Integer relationClientNbMissions;

    // ── Matrice de différenciation (générée par Claude) ─────────────────────
    /**
     * JSON : [{"critere":"Expertise hydraulique","positionEgis":"COUVERT",
     * "argumentGap":"3 références similaires en Afrique de l'Ouest"}, ...]
     */
    @Column(name = "matrice_diff", columnDefinition = "TEXT")
    private String matriceDiff;

    // ── Champs additionnels ──────────────────────────────────────────────────

    /** [[QUALIFS_EXIGEES]] — extrait par Claude */
    @Column(name = "qualifs_exigees", columnDefinition = "TEXT")
    private String qualifsExigees;

    /** [[GAP_QUALIFS]] — comparaison avec profil Egis */
    @Column(name = "gap_qualifs", columnDefinition = "TEXT")
    private String gapQualifs;

    /** [[SECTEUR_AO]] — secteur détecté */
    @Column(name = "secteur_ao", length = 200)
    private String secteurAo;

    /** [[TYPE_CONTRAT]] — type de contrat détecté */
    @Column(name = "type_contrat", length = 100)
    private String typeContrat;

    /** [[ALIGNEMENT_STRATEGIQUE]] — "Oui"|"Partiel"|"Non" */
    @Column(name = "alignement_strategique", length = 20)
    private String alignementStrategique;

    // ── Décision de compatibilité Phase 3 ──────────────────────────────────

    /** True si le dossier passe les seuils de compatibilité pour générer la méthodologie */
    @Column(name = "compatible_methodologie")
    private Boolean compatibleMethodologie;

    /** Motif(s) si non compatible, séparés par " · " — explique quel seuil a échoué */
    @Column(name = "motif_incompatibilite", length = 500)
    private String motifIncompatibilite;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}