package tn.rihab.analysteservice.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Vue d'une entrée d'audit, déserialisée depuis
 * GET /api/dossiers/{id}/audit (project-service).
 * Utilisée par AuditGenerationService (Phase 6) pour construire
 * le rapport d'audit narratif via ia-service.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditEntryDto {
    private UUID id;
    private UUID dossierId;
    private String action;
    private String acteur;
    private String detail;
    private String statusAvant;
    private String statusApres;
    private LocalDateTime timestamp;
}