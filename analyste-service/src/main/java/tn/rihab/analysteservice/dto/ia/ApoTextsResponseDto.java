package tn.rihab.analysteservice.dto.ia;


import lombok.*;
import java.util.List;


@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApoTextsResponseDto {

    @com.fasterxml.jackson.annotation.JsonProperty("RESUME_CONTEXTE_OBJECTIFS")
    private String resumeContexteObjectifs;

    @com.fasterxml.jackson.annotation.JsonProperty("POINTS_CRITIQUES")
    private String pointsCritiques;

    @com.fasterxml.jackson.annotation.JsonProperty("RECOMMANDATION_GO_NOGO")
    private String recommandationGoNogo;

    @com.fasterxml.jackson.annotation.JsonProperty("ARGUMENTAIRE_GO_NOGO")
    private String argumentaireGoNogo;

    @com.fasterxml.jackson.annotation.JsonProperty("LISTE_CLARIFICATIONS")
    private String listeClarifications;

    /** [[ANALYSE_CONCURRENCE]] — forces/faiblesses anticipées concurrents */
    private String analyseConcurrence;

    /** [[JUSTIF_SHORTLIST]] — argumentation de la shortlist */
    private String justifShortlist;

    /** [[JUSTIF_DELAI_PREP]] — justification du délai de préparation */
    private String justifDelaiPrep;

    private java.util.Map<String, Object> stats;
}