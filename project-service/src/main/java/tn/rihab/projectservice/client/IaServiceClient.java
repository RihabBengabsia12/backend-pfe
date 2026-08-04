package tn.rihab.projectservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import tn.rihab.projectservice.config.FeignConfig;
import tn.rihab.projectservice.dto.ExtractionRequestDto;
import tn.rihab.projectservice.dto.ExtractionResponseDto;

/**
 * Client Feign vers ia-service (Python FastAPI, port 8000).
 *
 * ia-service N'EST PAS enregistré dans Eureka (service Python).
 * L'URL est définie dans application.yml : ia.service.url
 *
 * Appels effectués par project-service :
 *   POST /extract/phase1    → extraction synchrone des 12 champs bloquants
 *   POST /extract/refield   → ré-extraction ciblée d'un seul champ
 *
 * Les appels Phase 2, 3, 4 sont effectués par analyste-service.
 */
@FeignClient(
        name      = "ia-service",
        url       = "${IA_SERVICE_URL:http://localhost:8000}",
        configuration = FeignConfig.class
)
public interface IaServiceClient {

    /**
     * Extraction Phase 1 : 12 champs bloquants.
     * Appel synchrone — l'utilisateur attend la réponse dans l'interface.
     * Timeout : 180s (défini dans FeignConfig).
     */
    @PostMapping("/extract/phase1")
    ExtractionResponseDto extractPhase1(@RequestBody ExtractionRequestDto request);

    /**
     * Ré-extraction ciblée d'un seul champ.
     * Utilisé quand l'utilisateur clique sur "Ré-extraire" pour un champ spécifique.
     * Le fieldName est encodé dans le DTO (phase = "REFIELD_PAYS" etc.)
     */
    @PostMapping("/extract/refield")
    ExtractionResponseDto reextractField(@RequestBody ExtractionRequestDto request);

    @GetMapping("/config/prompts")
    java.util.Map<String, String> getAllPrompts();

    @PutMapping("/config/prompts/{filename}")
    java.util.Map<String, String> updatePrompt(@PathVariable("filename") String filename, @RequestBody tn.rihab.projectservice.dto.PromptUpdateDto request);
}