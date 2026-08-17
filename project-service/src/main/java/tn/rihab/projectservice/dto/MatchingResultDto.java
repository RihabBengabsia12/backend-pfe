package tn.rihab.projectservice.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class MatchingResultDto {
    private UUID id;
    private UUID dossierId;
    private Double tauxCouvertureCompetences;
    private Double tauxCouvertureExperts;
    private String gapRefs; // JSON string
    private String alignementStrategique;
    private Boolean compatibleMethodologie;
}
