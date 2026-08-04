package tn.rihab.analysteservice.dto.ia;



import lombok.*;
import java.util.List;
import java.util.UUID;

/** Contexte envoyé à ia-service pour générer le rapport No-Go narratif. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NoGoContextDto {
    private UUID dossierId;
    private String intituleOffre;
    private String client;
    private String pays;
    private Double pwinScore;
    private Double scoreA, scoreB, scoreC, scoreD, scoreE;
    private String decisionAuto;
    private List<ApoGenerationContextDto.RisqueContexte> risques;
    private String motifPrincipal;
}