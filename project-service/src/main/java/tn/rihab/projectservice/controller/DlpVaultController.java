package tn.rihab.projectservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.rihab.projectservice.service.AnonymizationService;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class DlpVaultController {

    private final AnonymizationService anonymizationService;

    @PostMapping("/anonymization/mask")
    public ResponseEntity<String> maskText(@RequestBody String text) {
        return ResponseEntity.ok(anonymizationService.maskText(text));
    }

    @PostMapping("/anonymization/unmask")
    public ResponseEntity<Map<String, String>> unmaskMap(@RequestBody Map<String, String> values) {
        return ResponseEntity.ok(anonymizationService.unmaskMap(values));
    }

    @PostMapping("/dossiers/{id}/decapsulate")
    public ResponseEntity<String> decapsulate(@PathVariable("id") UUID dossierId, @RequestBody String content) {
        // We use the same unmaskText logic, dossierId is kept for API compatibility
        return ResponseEntity.ok(anonymizationService.unmaskText(content));
    }
}
