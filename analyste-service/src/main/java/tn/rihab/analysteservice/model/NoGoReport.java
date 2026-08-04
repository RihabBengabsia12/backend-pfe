package tn.rihab.analysteservice.model;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Rapport No-Go généré quand le P-Win score est sous le seuil.
 * Document narratif généré par Claude (NoGoReportService),
 * exporté en DOCX via Rapport-Audit-Template ou template dédié.
 */
@Entity
@Table(name = "nogo_reports")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NoGoReport {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "dossier_id", nullable = false, unique = true)
    private UUID dossierId;

    @Column(name = "pwin_score")
    private Double pwinScore;

    /**
     * Les 3-5 motifs principaux du No-Go, format JSON :
     * [{"axe":"C","champ":"PENALITES","valeur":"Rédhibitoire","poids":"critique"}, ...]
     */
    @Column(name = "motifs_principaux", columnDefinition = "TEXT")
    private String motifsPrincipaux;

    /** Texte narratif généré par Claude (200-300 mots) */
    @Column(name = "analyse_narrative", columnDefinition = "TEXT")
    private String analyseNarrative;

    /** Chemin MinIO du rapport No-Go DOCX généré */
    @Column(name = "docx_path", length = 500)
    private String docxPath;

    @CreationTimestamp
    @Column(name = "generated_at", updatable = false)
    private LocalDateTime generatedAt;
}