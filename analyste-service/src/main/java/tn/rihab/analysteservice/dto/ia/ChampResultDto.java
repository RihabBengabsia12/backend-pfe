package tn.rihab.analysteservice.dto.ia;

import lombok.*;

/** Résultat d'extraction d'un champ unique — identique au format project-service. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChampResultDto {
    private String valeur;
    private Double confiance;
    private String source;
}