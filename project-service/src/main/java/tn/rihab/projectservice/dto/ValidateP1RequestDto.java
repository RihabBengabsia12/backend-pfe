package tn.rihab.projectservice.dto;

import lombok.*;
import java.util.Map;

/** DTO de validation Phase 1 envoyé par Angular après que l'utilisateur confirme les champs. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ValidateP1RequestDto {

    /** Clé = nom champ (ex "PAYS"), valeur = donnée validée */
    private Map<String, ChampValide> champs;

    /** 1=Urgent 2=Modéré 3=Normal — null = priorité auto */
    private Integer prioriteOverride;

    /** Code MI interne Egis (optionnel à ce stade) */
    private String numeroReference;

    /** Date arrivée Back Office JJ/MM/AAAA (optionnel) */
    private String arriveBo;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ChampValide {
        private String  valeur;
        /** true si l'utilisateur a modifié la valeur Claude */
        private Boolean humanModified;
    }
}