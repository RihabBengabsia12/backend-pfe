package tn.rihab.analysteservice.dto.ia;


import lombok.*;
import java.util.UUID;

/** Requête vers /generate/checklist — pièces pointées selon bailleur. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChecklistRequestDto {
    private UUID dossierId;
    /** Ex: "Banque Mondiale", "AFD", "Union Européenne (FED)" */
    private String bailleurs;
    private String pays;
    private String typeContrat;
}