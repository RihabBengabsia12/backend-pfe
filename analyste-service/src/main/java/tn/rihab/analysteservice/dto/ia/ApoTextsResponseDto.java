package tn.rihab.analysteservice.dto.ia;


import lombok.*;
import java.util.List;


@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApoTextsResponseDto {

    /** [[RESUME_CONTEXTE_OBJECTIFS]] — 5 lignes max, factuel */
    private String resumeContexteObjectifs;

    /** [[POINTS_CRITIQUES]] — liste des alertes prioritaires */
    private String pointsCritiques;

    /** [[RECOMMANDATION_GO_NOGO]] — "Go" | "No-Go" | "Go conditionnel (...)" */
    private String recommandationGoNogo;

    /** [[ARGUMENTAIRE_GO_NOGO]] — 100-200 mots structurés */
    private String argumentaireGoNogo;

    /** [[LISTE_CLARIFICATIONS]] — questions à poser au client */
    private String listeClarifications;

    /** [[ANALYSE_CONCURRENCE]] — forces/faiblesses anticipées concurrents */
    private String analyseConcurrence;

    /** [[JUSTIF_SHORTLIST]] — argumentation de la shortlist */
    private String justifShortlist;

    /** [[JUSTIF_DELAI_PREP]] — justification du délai de préparation */
    private String justifDelaiPrep;
}