package tn.rihab.projectservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.rihab.projectservice.dto.ChampResult;
import tn.rihab.projectservice.dto.ValidateP1RequestDto;
import tn.rihab.projectservice.model.entity.Dossier;
import tn.rihab.projectservice.model.entity.ExtractionMetadata;
import tn.rihab.projectservice.service.DossierService;
import tn.rihab.projectservice.service.ExtractionService;
import tn.rihab.projectservice.service.StorageService;

import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/dossiers")
@RequiredArgsConstructor
@Slf4j
public class ExtractionController {

    private final DossierService    dossierService;
    private final ExtractionService extractionService;
    private final StorageService    storageService;

    // ── GET /api/dossiers/{id}/extraction-p1 ──────────────────────────────────


    @GetMapping("/{id}/extraction-p1")
    public ResponseEntity<List<ExtractionMetadata>> getExtractionP1(
            @PathVariable UUID id) {

        List<ExtractionMetadata> champs = dossierService.getExtractionP1(id);
        return ResponseEntity.ok(champs);
    }

    // ── PUT /api/dossiers/{id}/validate-p1 ────────────────────────────────────


    @PutMapping("/{id}/validate-p1")
    public ResponseEntity<Dossier> validateP1(
            @PathVariable UUID id,
            @RequestBody ValidateP1RequestDto request) {

        if (request.getChamps() == null || request.getChamps().isEmpty()) {
            throw new IllegalArgumentException("La liste des champs validés est obligatoire");
        }

        log.info("[Validation P1] Dossier {} — {} champs soumis, humanModified={}",
                id,
                request.getChamps().size(),
                request.getChamps().values().stream()
                        .filter(c -> Boolean.TRUE.equals(c.getHumanModified()))
                        .count());

        Dossier dossier = dossierService.validateP1(id, request);
        return ResponseEntity.ok(dossier);
    }

    // ── PUT /api/dossiers/{id}/reextract-field ─────────────────────────────────

    @PutMapping("/{id}/reextract-field")
    public ResponseEntity<ChampResult> reextractField(
            @PathVariable UUID id,
            @RequestParam String fieldName) {

        log.info("[Ré-extraction] Dossier {} — champ [[{}]]", id, fieldName);

        // Récupérer le texte du document
        String documentText = dossierService.getDocumentText(id);

        // Ré-extraire via ia-service
        ChampResult result = extractionService.reextractField(id, fieldName, documentText);

        return ResponseEntity.ok(result);
    }
}