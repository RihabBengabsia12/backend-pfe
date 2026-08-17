package tn.rihab.analysteservice.dto.ia;


import lombok.*;
import java.util.List;

/** Matrice de différenciation générée par Claude (Phase 3). */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MatrixResponseDto {

    /** Lignes de la matrice : critère | position Egis | argument/gap */
    private List<MatriceRow> lignes;

    // Audit metrics
    private Integer token_usage;
    private Integer input_tokens;
    private Integer output_tokens;
    private Integer processing_time_ms;
    private Double estimated_cost;
    private Integer cache_creation_tokens;
    private Integer cache_read_tokens;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class MatriceRow {
        private String critere;
        /** COUVERT | PARTIELLEMENT | NON_COUVERT | VIA_PARTENAIRE */
        private String positionEgis;
        private String argumentGap;
    }
}