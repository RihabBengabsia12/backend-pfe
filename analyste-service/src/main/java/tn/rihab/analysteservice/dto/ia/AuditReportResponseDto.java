package tn.rihab.analysteservice.dto.ia;



import lombok.*;

/** Rapport d'audit narratif généré par Claude (Phase 6). */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditReportResponseDto {
    /** Résumé narratif du cycle de vie du dossier */
    private String narratif;
    /** Points d'amélioration identifiés pour les prochaines analyses similaires */
    private String pointsAmelioration;
    
    @com.fasterxml.jackson.annotation.JsonProperty("rapport")
    @com.fasterxml.jackson.annotation.JsonAlias("report")
    private String rapport;

    private java.util.Map<String, Object> stats;
}
