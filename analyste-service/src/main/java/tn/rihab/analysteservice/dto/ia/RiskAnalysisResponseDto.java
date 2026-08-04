package tn.rihab.analysteservice.dto.ia;

import lombok.*;

import java.util.Map;

/**
 * Réponse de POST /extract/risks — 10 risques évalués par Claude.
 *
 * Clés attendues dans risques (10 exactement) :
 *   RISQUE_PAYS_SECURITE, RISQUES_FINANCIERS, PENALITES,
 *   EXIGENCES_TDR_INACCEPTABLES, GARANTIES_ASSURANCES_ELEVEES,
 *   TAILLE_DISPERSION, FRAIS_DIVERS_ELEVES, BUDGET_FAIBLE_HM_LIMITES,
 *   PARTICIPATION_LOCALE_EXCESSIVE, FISCALITE_NON_MAITRISEE
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RiskAnalysisResponseDto {
    // Utilisation directe du nom de la classe, car elle partage le même package racine tn.rihab.analysteservice.dto.ia
    private Map<String, RiskItemDto> risques;
    
    // Audit metrics
    private Integer token_usage;
    private Integer processing_time_ms;
    private Double estimated_cost;
    private Integer cache_creation_tokens;
    private Integer cache_read_tokens;
}