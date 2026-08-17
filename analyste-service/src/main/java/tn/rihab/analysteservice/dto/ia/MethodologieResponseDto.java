package tn.rihab.analysteservice.dto.ia;


import lombok.*;
import java.util.List;
import java.util.UUID;

/** 5 sections de méthodologie générées par Claude (Phase 4). */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MethodologieResponseDto {
    @com.fasterxml.jackson.annotation.JsonProperty("SECTION1_COMPREHENSION")
    private String section1_contexteEnjeux;
    
    @com.fasterxml.jackson.annotation.JsonProperty("SECTION2_METHODOLOGIE")
    private String section2_approchMethodologique;
    
    @com.fasterxml.jackson.annotation.JsonProperty("SECTION3_PLAN_TRAVAIL")
    private String section3_planTravail;
    
    @com.fasterxml.jackson.annotation.JsonProperty("SECTION4_EQUIPE")
    private String section4_compositionEquipe;
    
    @com.fasterxml.jackson.annotation.JsonProperty("SECTION5_REFERENCES")
    private String section5_gestionRisques;
    
    private java.util.Map<String, Object> stats;
}