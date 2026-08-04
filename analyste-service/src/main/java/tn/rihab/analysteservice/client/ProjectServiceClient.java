package tn.rihab.analysteservice.client;

import tn.rihab.analysteservice.config.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import tn.rihab.analysteservice.dto.AuditEntryDto;
import tn.rihab.analysteservice.dto.DossierDto;

import java.util.UUID;

/**
 * Client Feign vers project-service, enregistré dans Eureka
 * sous le nom PROJECT-SERVICE (lb://PROJECT-SERVICE).
 *
 * Utilisé pour :
 *  - Récupérer le texte brut AP+TDR (Phases 2, 3, 4 — extraction Claude)
 *  - Récupérer les champs Phase 1 déjà validés (Phase 4 — assemblage APO)
 *  - Récupérer l'historique d'audit (Phase 6)
 */
@FeignClient(
        name = "PROJECT-SERVICE",
        configuration = FeignConfig.class
)
public interface ProjectServiceClient {

    /** Texte brut AP+TDR fusionnés, stocké sur MinIO par project-service. */
    @GetMapping("/api/dossiers/{id}/document-text")
    String getDocumentText(@PathVariable("id") UUID dossierId);

    /** Dossier complet (champs Phase 1 + statut + chemins MinIO). */
    @GetMapping("/api/dossiers/{id}")
    DossierDto getDossier(@PathVariable("id") UUID dossierId);

    /** Historique d'audit complet — utilisé par AuditGenerationService. */
    @GetMapping("/api/dossiers/{id}/audit")
    java.util.List<AuditEntryDto> getAuditHistory(@PathVariable("id") UUID dossierId);

    @PostMapping("/api/dossiers/{id}/notify-analyst")
    void notifyAnalyst(@PathVariable("id") UUID dossierId);

    @org.springframework.web.bind.annotation.PostMapping("/api/anonymization/unmask")
    java.util.Map<String, String> unmaskMap(@org.springframework.web.bind.annotation.RequestBody java.util.Map<String, String> values);

    @org.springframework.web.bind.annotation.PostMapping("/api/anonymization/mask")
    String maskText(@org.springframework.web.bind.annotation.RequestBody String text);

    @org.springframework.web.bind.annotation.PostMapping("/api/dossiers/{id}/decapsulate")
    String decapsulate(@PathVariable("id") UUID dossierId, @org.springframework.web.bind.annotation.RequestBody String content);
}