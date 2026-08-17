package tn.rihab.projectservice.dto;

import lombok.*;
import java.util.List;
import java.util.Map;

/**
 * Réponse complète d'ia-service après extraction.
 * Contient les champs extraits + scores de confiance + alertes.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExtractionResponseDto {

    /**
     * Map des champs extraits.
     * Clé   = nom du placeholder sans crochets (ex: "PAYS", "DT_LIM_SOUM")
     * Valeur = ChampResult { valeur, confiance 0.0-1.0, source citation }
     */
    private Map<String, ChampResult> champs;

    /**
     * Alertes non bloquantes → panneau ambre dans l'interface.
     * Ex: "BUDGET_GLOBAL confiance faible — distinguer honoraires vs projet total"
     */
    private List<String> alertes;

    /**
     * Alertes bloquantes → panneau rouge, action immédiate requise.
     * Ex: "VISITE_DATE déjà passée — disqualification imminente"
     */
    private List<String> alertesBloquantes;

    /** TJM implicite calculé côté ia-service = budget / HM / 20 */
    private Double tjmImplicite;

    /** True si TJM hors plage [350€-3000€/j] */
    private Boolean tjmHorsPlage;

    // Métriques IA (FinOps & Perf)
    private Integer tokenUsage;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer processingTimeMs;
    private Double estimatedCost;
}