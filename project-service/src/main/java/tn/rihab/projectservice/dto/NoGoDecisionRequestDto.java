package tn.rihab.projectservice.dto;

import lombok.Data;

@Data
public class NoGoDecisionRequestDto {
    private String decision; // "VALIDATE_NOGO" or "FORCE_GO"
    private String typeForcage;
    private String justification;
    private String managerName;
}
