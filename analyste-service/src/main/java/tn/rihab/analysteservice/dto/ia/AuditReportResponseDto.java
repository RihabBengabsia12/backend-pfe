package tn.rihab.analysteservice.dto.ia;



import lombok.*;

/** Rapport d'audit narratif généré par Claude (Phase 6). */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditReportResponseDto {
    /** Résumé narratif du cycle de vie du dossier */
    private String narratif;
    /** Points d'amélioration identifiés pour les prochaines analyses similaires */
    private String pointsAmelioration;
}