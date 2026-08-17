package tn.rihab.analysteservice.dto.ia;


import lombok.*;

/** Rapport No-Go narratif généré par Claude. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NoGoReportResponseDto {
    /** Analyse narrative 200-300 mots expliquant pourquoi l'opportunité ne justifie pas l'investissement */
    private String analyseNarrative;
    /** JSON des 3-5 motifs principaux structurés */
    private String motifsPrincipaux;
    
    private java.util.Map<String, Object> stats;
}