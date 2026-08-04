package tn.rihab.projectservice.dto;

import lombok.Data;

@Data
public class ChampResult {
    private Object valeur;
    private Double confiance;
    private String source;
}