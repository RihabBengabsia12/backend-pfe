package tn.rihab.projectservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.rihab.projectservice.model.entity.ValidationToken;
import tn.rihab.projectservice.service.ValidationService;

import java.util.List;
import java.util.Map;
import java.util.UUID;


@RestController
@RequestMapping("/api/validation")
@RequiredArgsConstructor
@Slf4j
public class ValidationController {

    private final ValidationService validationService;

    // ── POST /api/validation/{id}/send ────────────────────────────────────────


    @PostMapping("/{id}/send")
    public ResponseEntity<List<Map<String, Object>>> sendToValidators(
            @PathVariable UUID id) {

        log.info("[Validation] Envoi aux validateurs pour dossier {}", id);

        List<ValidationToken> tokens = validationService.sendToValidators(id);

        // Retourner une vue sécurisée (sans le token opaque)
        List<Map<String, Object>> response = tokens.stream()
                .map(t -> Map.<String, Object>of(
                        "role",   t.getValidateurRole(),
                        "email",  t.getValidateurEmail(),
                        "nom",    t.getValidateurNom() != null ? t.getValidateurNom() : "",
                        "status", t.getStatus(),
                        "expireAt", t.getExpiresAt().toString()
                ))
                .toList();

        return ResponseEntity.ok(response);
    }

    // ── GET /api/validation/{id}/status ──────────────────────────────────────


    @GetMapping("/{id}/status")
    public ResponseEntity<List<Map<String, Object>>> getValidationStatus(
            @PathVariable UUID id) {

        List<ValidationToken> tokens = validationService.getValidationStatus(id);

        List<Map<String, Object>> response = tokens.stream()
                .filter(t -> !"CANCELLED".equals(t.getStatus()))
                .map(t -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("role",        t.getValidateurRole());
                    m.put("nom",         t.getValidateurNom() != null ? t.getValidateurNom() : "");
                    m.put("email",       t.getValidateurEmail());
                    m.put("status",      t.getStatus());
                    m.put("actionAt",    t.getActionAt() != null ? t.getActionAt().toString() : null);
                    m.put("commentaire", t.getCommentaire() != null ? t.getCommentaire() : "");
                    return m;
                })
                .toList();

        return ResponseEntity.ok(response);
    }

    // ── PUT /api/validation/token/{token} ─────────────────────────────────────


    @PutMapping("/token/{token}")
    public ResponseEntity<Map<String, String>> processTokenAction(
            @PathVariable String token,
            @RequestParam String action,
            @RequestParam(required = false, defaultValue = "") String commentaire) {

        log.info("[Token] Action {} sur token {}", action, token.substring(0, 8) + "...");

        String message = validationService.processAction(token, action, commentaire);

        return ResponseEntity.ok(Map.of(
                "status",  "SUCCESS",
                "action",  action,
                "message", message
        ));
    }

    // ── POST /api/validation/{id}/reminder/{role} ─────────────────────────────


    @PostMapping("/{id}/reminder/{role}")
    public ResponseEntity<Map<String, String>> sendReminder(
            @PathVariable UUID id,
            @PathVariable String role) {

        log.info("[Reminder] Rappel envoyé à {} pour dossier {}", role, id);
        validationService.sendReminder(id, role);

        return ResponseEntity.ok(Map.of(
                "status",  "SENT",
                "message", "Rappel envoyé à " + role
        ));
    }

    // ── GET /api/validation/pending ───────────────────────────────────────────


    @GetMapping("/pending")
    public ResponseEntity<List<Map<String, Object>>> getPendingValidations(
            @RequestParam String email) {

        log.info("[Validation] Récupération des dossiers en attente pour {}", email);

        List<Map<String, Object>> pending = validationService.getPendingValidationsForManager(email);
        return ResponseEntity.ok(pending);
    }
}