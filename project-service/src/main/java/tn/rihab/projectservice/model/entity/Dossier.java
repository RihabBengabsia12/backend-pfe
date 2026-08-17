package tn.rihab.projectservice.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import tn.rihab.projectservice.model.DossierStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entité principale d'un dossier d'appel d'offre.
 */
@Entity
@Table(name = "dossiers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Dossier {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    // ════════════════════════════════════════════════════════════
    // PHASE 1 — 12 champs bloquants extraits par Claude
    // ════════════════════════════════════════════════════════════

    @Column(length = 100)
    private String pays;

    @Column(name = "intitule_offre", length = 1000)
    private String intituleOffre;

    @Column(length = 300)
    private String client;

    @Column(length = 500)
    private String bailleurs;

    @Column(name = "budget_global", length = 200)
    private String budgetGlobal;

    @Column(name = "hommes_mois")
    private Double hommesMois;

    /** [[DT_LIM_SOUM]] Date limite de soumission (Saisie manuelle utilisateur) */
    @Column(name = "dt_lim_soum")
    private LocalDate dtLimSoum;

    @Column(length = 100)
    private String langue;

    @Column(name = "visite_obl")
    private Boolean visiteObl;

    @Column(name = "visite_date")
    private LocalDate visiteDate;

    @Column(name = "conf_obl")
    private Boolean confObl;

    @Column(name = "conf_date")
    private LocalDate confDate;

    // ════════════════════════════════════════════════════════════
    // Champs internes Egis
    // ════════════════════════════════════════════════════════════

    @Column(name = "numero_reference", length = 100)
    private String numeroReference;

    @Column(name = "arrivee_bo")
    private LocalDate arriveBo;

    @Column(name = "transmission")
    private LocalDate transmission;

    // ════════════════════════════════════════════════════════════
    // Champs calculés
    // ════════════════════════════════════════════════════════════

    @Column(name = "tjm_implicite")
    private Double tjmImplicite;

    @Column(name = "jours_ouvrables_restants")
    private Integer joursOuvrables;

    // ════════════════════════════════════════════════════════════
    // Cycle de vie
    // ════════════════════════════════════════════════════════════

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private DossierStatus status = DossierStatus.UPLOADED;

    @Column @Builder.Default
    private Integer priorite = 2;

    @Column(name = "priority_override") @Builder.Default
    private Boolean priorityOverride = false;

    @Column(name = "manager_notified") @Builder.Default
    private Boolean managerNotified = false;

    @Column(name = "created_by_email", length = 150)
    private String createdByEmail;

    @Column(name = "analyst_notified") @Builder.Default
    private Boolean analystNotified = false;

    @Column(name = "confiance_p1")
    private Double confianceP1;

    @Column(name = "pwin_score")
    private Double pwinScore;

    @Column(name = "is_private")
    @Builder.Default
    private Boolean isPrivate = false;

    // ════════════════════════════════════════════════════════════
    // Stockage MinIO
    // ════════════════════════════════════════════════════════════

    @Deprecated
    @Column(name = "document_ap_path", length = 500)
    private String documentApPath;

    @Column(name = "document_tdr_path", length = 500)
    private String documentTdrPath;

    @Column(name = "document_text_path", length = 500)
    private String documentTextPath;

    /**
     * Copie de travail anonymisée d'un dossier privé. Elle est utilisée par les
     * phases IA et ne remplace jamais le texte extrait original.
     */
    @Column(name = "anonymized_text_path", length = 500)
    private String anonymizedTextPath;

    @Column(name = "apo_docx_path", length = 500) private String apoDocxPath;
    @Column(name = "methodo_docx_path", length = 500) private String methodoDocxPath;
    @Column(name = "rapport_path", length = 500) private String rapportPath;
    @Column(name = "nogo_report_path", length = 500) private String nogoReportPath;
    @Column(name = "pack_zip_path", length = 500) private String packZipPath;
    @Column(name = "audit_report_path", length = 500) private String auditReportPath;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
