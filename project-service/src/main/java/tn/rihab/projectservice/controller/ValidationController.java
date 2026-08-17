package tn.rihab.projectservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
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

    // ── GET /api/validation/{id}/targets ──────────────────────────────────────
    @GetMapping("/{id}/targets")
    public ResponseEntity<List<Map<String, Object>>> getValidationTargets(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "GO") String type) {
        return ResponseEntity.ok(validationService.getValidationTargets(id, type));
    }

    /** Téléchargement public et temporaire des livrables depuis le lien reçu par e-mail. */
    @GetMapping("/token/{token}/document/{type}")
    public ResponseEntity<byte[]> downloadDocumentFromValidationEmail(
            @PathVariable String token,
            @PathVariable String type) {
        log.info("[Validation] Consultation du document {} via lien e-mail", type);
        var document = validationService.getDocumentForValidationToken(token, type);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        // Un DOCX/ZIP ne peut pas être prévisualisé nativement par la plupart
                        // des navigateurs : le téléchargement est le comportement fiable.
                        "attachment; filename=\"" + document.filename() + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType(document.contentType()))
                .body(document.content());
    }

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
                    m.put("decisionSource", t.getDecisionSource() != null ? t.getDecisionSource() : "PLATFORM");
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
            @RequestParam(required = false, defaultValue = "") String commentaire,
            @RequestParam(required = false) String source,
            Authentication authentication) {

        log.info("[Token] Action {} sur token {}", action, token.substring(0, 8) + "...");

        String effectiveSource = source;
        if (effectiveSource == null || effectiveSource.isBlank()) {
            effectiveSource = authentication != null
                    && authentication.isAuthenticated()
                    && !(authentication instanceof AnonymousAuthenticationToken)
                    ? "PLATFORM" : "EMAIL";
        }
        String message = validationService.processAction(token, action, commentaire, effectiveSource);

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


    @GetMapping("/target-manager")
    public ResponseEntity<Map<String, Object>> getTargetManager(@RequestParam double budget) {
        return ResponseEntity.ok(validationService.getTargetManagerForBudget(budget));
    }

    @GetMapping("/pending")
    public ResponseEntity<List<Map<String, Object>>> getPendingValidations(
            @RequestParam String email) {

        log.info("[Validation] Récupération des dossiers en attente pour {}", email);

        List<Map<String, Object>> pending = validationService.getPendingValidationsForManager(email);
        return ResponseEntity.ok(pending);
    }

    @PostMapping("/{id}/generate-audit")
    public ResponseEntity<Map<String, String>> generateAudit(
            @PathVariable UUID id) {
        validationService.generateAuditForManager(id);
        return ResponseEntity.accepted().body(Map.of(
                "status", "AUDIT_GENERATING",
                "message", "La génération du rapport d'audit de la décision finale a été lancée. Le dossier sera archivé à la réception du rapport."
        ));
    }
}
