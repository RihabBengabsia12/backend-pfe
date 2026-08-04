package tn.rihab.analysteservice.dto.ia;


import lombok.*;
import java.util.List;
import java.util.UUID;

/** 5 sections de méthodologie générées par Claude (Phase 4). */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MethodologieResponseDto {
    private String section1_contexteEnjeux;
    private String section2_approchMethodologique;
    private String section3_planTravail;
    private String section4_compositionEquipe;
    private String section5_gestionRisques;
}