package tn.rihab.analysteservice.dto.ia;



import tn.rihab.analysteservice.dto.AuditEntryDto;
import lombok.*;
import java.util.List;
import java.util.UUID;

/** Contexte complet envoyé à ia-service pour le rapport d'audit Phase 6. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditContextDto {
    private UUID dossierId;
    private String intituleOffre;
    private String client;
    private Double pwinScore;
    private String decisionFinale;
    private List<AuditEntryDto> auditTrail;
    private Integer nbChampsCorrigesHumain;
    private Boolean aEuForceGo;
}