package tn.rihab.projectservice.dto;

import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** DTO envoyé à ia-service pour déclencher une extraction Claude. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExtractionRequestDto {
    @JsonProperty("dossierId")
    private UUID   dossierId;

    @JsonProperty("documentText")
    private String documentText;

    @JsonProperty("fieldName")
    private String fieldName;

    /** "P1" | "P2" | "RISKS" | "REFIELD_PAYS" etc. */
    @Builder.Default
    private String phase = "P1";

    @JsonProperty("custom_prompts")
    @Builder.Default
    private java.util.Map<String, String> customPrompts = new java.util.HashMap<>();
}