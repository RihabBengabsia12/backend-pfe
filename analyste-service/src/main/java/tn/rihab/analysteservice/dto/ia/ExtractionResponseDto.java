package tn.rihab.analysteservice.dto.ia;

import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * Réponse de POST /extract/phase2 — 16 champs Phase 2
 * (hors les 10 risques, retournés séparément par RiskAnalysisResponseDto).
 *
 * Clés attendues dans champs :
 *   DATE_LIMITE_SOUMISSION, DELAI_GLOBAL_MOIS, DATE_LIMITE_QUESTIONS,
 *   MODE_NOTATION, NOTE_MINIMALE, PON_TECH, PON_FIN,
 *   FIN_LOCAL_OUI_NON, FINA_LOCAL_DETAILS,
 *   CAUTION_MONTANT, CAUTION_MONNAIE, CAUTION_DUREE, BANQUE_LOCALE_EXIGEE,
 *   LISTE_CLARIFICATIONS
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExtractionResponseDto {
    private Map<String, ChampResultDto> champs;
    private List<String> alertes;
    private List<String> alertesBloquantes;
    
    // Audit metrics
    private Integer token_usage;
    private Integer input_tokens;
    private Integer output_tokens;
    private Integer processing_time_ms;
    private Double estimated_cost;
    private Integer cache_creation_tokens;
    private Integer cache_read_tokens;
}