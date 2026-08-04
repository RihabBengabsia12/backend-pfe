package tn.rihab.analysteservice.dto.ia;


import lombok.*;
import java.util.List;
import java.util.UUID;

/** Contexte envoyé à ia-service pour générer la matrice de différenciation. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MatchingContextDto {

    private UUID dossierId;

    /** Exigences extraites de la DP */
    private List<String> refsExigees;
    private List<String> qualifsExigees;
    private List<RequirementsResponseDto.ExpertRequisDto> expertsRequis;

    /** Résultats du matching côté Egis */
    private Double tauxCouvertureCompetences;
    private Double tauxCouvertureExperts;
    private String gapRefs;      // JSON
    private String gapQualifs;   // JSON

    /** Contexte dossier */
    private String pays;
    private String secteur;
    private String bailleurs;
}