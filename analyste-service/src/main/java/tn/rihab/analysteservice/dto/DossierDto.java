package tn.rihab.analysteservice.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Vue simplifiée du Dossier de project-service, déserialisée depuis
 * GET /api/dossiers/{id}.
 * Ne contient que les champs nécessaires à analyste-service (Phase 1
 * + chemins MinIO). Les champs Phase 2/3/4 sont dans AnalyseDossier
 * (base de données analyste-service).
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DossierDto {

    private UUID id;
    private String status;
    private Boolean isPrivate;

    // Phase 1
    private String pays;
    private String intituleOffre;
    private String numeroReference;
    private String client;
    private String bailleurs;
    private String budgetGlobal;
    private Double hommesMois;
    private LocalDate dtLimSoum;
    private String langue;
    private Boolean visiteObl;
    private LocalDate visiteDate;
    private Boolean confObl;
    private LocalDate confDate;
    private LocalDate arriveBo;
    private LocalDate transmission;

    // Calculés
    private Double tjmImplicite;
    private Integer joursOuvrables;
    private Integer priorite;
    private Double confianceP1;
    private Double pwinScore;

    // MinIO
    private String documentTextPath;
    private String apoDocxPath;
    private String methodoDocxPath;
    private String rapportPath;
    private String nogoReportPath;
    private String packZipPath;
    private String auditReportPath;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}