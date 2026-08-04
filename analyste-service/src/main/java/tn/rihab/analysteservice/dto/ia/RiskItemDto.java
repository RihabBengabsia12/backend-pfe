package tn.rihab.analysteservice.dto.ia;

import lombok.*;

/**
 * Évaluation d'un risque individuel par Claude.
 * niveau ∈ { "Faible", "Modéré", "Élevé", "Rédhibitoire" }
 * Score numérique correspondant (utilisé par AxeRisquesCalculator) :
 *   Faible=1.0, Modéré=0.7, Élevé=0.3, Rédhibitoire=0.0
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RiskItemDto {
    /** Faible | Modéré | Élevé | Rédhibitoire */
    private String niveau;
    /** Justification courte (2-3 lignes), basée sur signaux détectés dans la DP */
    private String justification;
}