package tn.rihab.projectservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.rihab.projectservice.client.IaServiceClient;
import tn.rihab.projectservice.dto.PromptUpdateDto;

import java.util.Map;

@RestController
@RequestMapping("/api/dossiers/config-ia")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin("*")
public class ConfigIaController {

    private final IaServiceClient iaServiceClient;
    private final tn.rihab.projectservice.service.SystemAuditClientService systemAudit;

    @GetMapping("/prompts")
    public ResponseEntity<Map<String, String>> getAllPrompts() {
        log.info("Récupération de tous les prompts depuis ia-service");
        return ResponseEntity.ok(iaServiceClient.getAllPrompts());
    }

    @PutMapping("/prompts/{filename}")
    public ResponseEntity<Map<String, String>> updatePrompt(@PathVariable String filename, @RequestBody PromptUpdateDto request) {
        log.info("Mise à jour du prompt global: {}", filename);
        systemAudit.logSystemAction("IA_PROMPT_UPDATE", "Mise à jour du prompt IA global : " + filename);
        return ResponseEntity.ok(iaServiceClient.updatePrompt(filename, request));
    }

    private final tn.rihab.projectservice.repository.DossierPromptOverrideRepository overrideRepository;
    private final tn.rihab.projectservice.repository.DossierRepository dossierRepository;

    @GetMapping("/overrides/{dossierId}")
    public ResponseEntity<Map<String, String>> getOverrides(@PathVariable java.util.UUID dossierId) {
        java.util.List<tn.rihab.projectservice.model.entity.DossierPromptOverride> overrides = overrideRepository.findByDossierId(dossierId);
        Map<String, String> result = new java.util.HashMap<>();
        for (tn.rihab.projectservice.model.entity.DossierPromptOverride override : overrides) {
            result.put(override.getPromptFilename(), override.getCustomContent());
        }
        return ResponseEntity.ok(result);
    }

    @PutMapping("/overrides/{dossierId}/{filename}")
    public ResponseEntity<Void> updateOverride(@PathVariable java.util.UUID dossierId, @PathVariable String filename, @RequestBody PromptUpdateDto request) {
        tn.rihab.projectservice.model.entity.Dossier dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new RuntimeException("Dossier introuvable"));

        tn.rihab.projectservice.model.entity.DossierPromptOverride override = overrideRepository
                .findByDossierIdAndPromptFilename(dossierId, filename)
                .orElse(tn.rihab.projectservice.model.entity.DossierPromptOverride.builder()
                        .dossier(dossier)
                        .promptFilename(filename)
                        .build());
        
        override.setCustomContent(request.getContent());
        overrideRepository.save(override);
        return ResponseEntity.ok().build();
    }
}
