package tn.rihab.projectservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.rihab.projectservice.model.entity.AnonymizationDict;
import tn.rihab.projectservice.service.DlpService;

import java.util.List;

@RestController
@RequestMapping("/api/projects/anonymization")
@RequiredArgsConstructor
@Slf4j
public class AnonymizationController {

    private final DlpService dlpService;
    private final tn.rihab.projectservice.service.SystemAuditClientService systemAudit;

    @GetMapping
    public ResponseEntity<List<AnonymizationDict>> getAll() {
        return ResponseEntity.ok(dlpService.getAll());
    }

    @PostMapping
    public ResponseEntity<AnonymizationDict> addWord(@RequestBody AnonymizationDict dict) {
        log.info("[Admin] Ajout d'un nouveau mot sensible au dictionnaire DLP");
        AnonymizationDict saved = dlpService.addWord(dict.getOriginalWord());
        systemAudit.logSystemAction("DLP_ADD_WORD", "Nouveau mot sensible ajouté : " + dict.getOriginalWord());
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AnonymizationDict> toggleActive(@PathVariable Long id, @RequestBody AnonymizationDict dict) {
        log.info("[Admin] Modification de l'état (actif/inactif) du mot ID : {}", id);
        AnonymizationDict updated = dlpService.toggleActive(id, dict.getIsActive());
        systemAudit.logSystemAction("DLP_TOGGLE_WORD", "Mot '" + updated.getOriginalWord() + "' passé à : " + (updated.getIsActive() ? "ACTIF" : "INACTIF"));
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWord(@PathVariable Long id) {
        log.info("[Admin] Suppression d'un mot du dictionnaire DLP (ID : {})", id);
        dlpService.deleteWord(id);
        systemAudit.logSystemAction("DLP_DELETE_WORD", "Suppression du mot DLP ID : " + id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/regenerate")
    public ResponseEntity<Void> forceRegenerate() {
        log.info("[Admin] Demande manuelle de régénération de tous les codes DLP");
        dlpService.regenerateAllCodes();
        systemAudit.logSystemAction("DLP_REGENERATE_CODES", "Régénération massive des codes DLP");
        return ResponseEntity.ok().build();
    }

    @PostMapping("/decapsulate/{dossierId}")
    public ResponseEntity<String> decapsulate(@PathVariable java.util.UUID dossierId, @RequestBody String text) {
        log.info("[System] Demande de décapsulation pour le dossier {}", dossierId);
        String decapsulated = dlpService.decapsulate(text, dossierId);
        return ResponseEntity.ok(decapsulated);
    }
}
