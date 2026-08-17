package tn.rihab.analysteservice.dto.ia;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Requête générique d'extraction envoyée à ia-service. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExtractionRequestDto {
    private UUID dossierId;

    @JsonProperty("documentText")
    private String documentText;

    @JsonProperty("fieldName")
    private String fieldName;

    /** "P2" | "RISKS" | "REQUIREMENTS" */
    private String phase;

    @JsonProperty("referentiel")
    private Map<String, Object> referentiel;
}