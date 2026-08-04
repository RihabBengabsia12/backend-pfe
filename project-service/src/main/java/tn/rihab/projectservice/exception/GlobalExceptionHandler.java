package tn.rihab.projectservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Gestionnaire global d'exceptions pour project-service.
 * Toutes les exceptions non catchées remontent ici et sont
 * transformées en réponse JSON uniforme pour Angular.
 *
 * Format de réponse d'erreur :
 * {
 *   "timestamp": "2025-09-15T10:30:00",
 *   "status": 404,
 *   "error": "NOT_FOUND",
 *   "message": "Dossier non trouvé : uuid",
 *   "path": "/api/dossiers/uuid"
 * }
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── 404 — Ressource introuvable ────────────────────────────────────────────
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(IllegalArgumentException ex) {
        log.warn("[400/404] {}", ex.getMessage());
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage());
    }

    // ── 422 — État invalide du dossier ─────────────────────────────────────────
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidState(IllegalStateException ex) {
        log.warn("[422] {}", ex.getMessage());
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_STATE", ex.getMessage());
    }

    // ── 400 — Validation des champs DTO ───────────────────────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + " : " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("[400] Validation : {}", details);
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", details);
    }

    // ── 400 — Paramètre de requête manquant (ex: ?dateLimite=...) ──────────────
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("[400] Paramètre manquant : {}", ex.getParameterName());
        return build(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
                "Le paramètre requis '" + ex.getParameterName() + "' est manquant");
    }

    // ── 400 — Partie de requête multipart manquante (ex: fichier "tdr") ────────
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, Object>> handleMissingPart(MissingServletRequestPartException ex) {
        log.warn("[400] Partie manquante : {}", ex.getRequestPartName());
        return build(HttpStatus.BAD_REQUEST, "MISSING_PART",
                "Le fichier/partie requis '" + ex.getRequestPartName() + "' est manquant");
    }

    // ── 413 — Fichier trop volumineux ─────────────────────────────────────────
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleFileTooLarge(MaxUploadSizeExceededException ex) {
        log.warn("[413] Fichier trop volumineux");
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                "Le fichier dépasse la taille maximum autorisée (20 Mo)");
    }

    // ── 503 — ia-service indisponible ─────────────────────────────────────────
    @ExceptionHandler(feign.RetryableException.class)
    public ResponseEntity<Map<String, Object>> handleFeignTimeout(feign.RetryableException ex) {
        log.error("[503] ia-service indisponible : {}", ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, "IA_SERVICE_UNAVAILABLE",
                "Le service d'extraction IA est temporairement indisponible. Réessayez dans quelques instants.");
    }

    // ── 503 — Erreur générique Feign ──────────────────────────────────────────
    @ExceptionHandler(feign.FeignException.class)
    public ResponseEntity<Map<String, Object>> handleFeignError(feign.FeignException ex) {
        log.error("[Feign] Erreur ia-service {} : {}", ex.status(), ex.getMessage());
        if (ex.status() == 504) {
            return build(HttpStatus.GATEWAY_TIMEOUT, "IA_SERVICE_TIMEOUT",
                    "Timeout ia-service — le document est peut-être trop volumineux");
        }
        return build(HttpStatus.BAD_GATEWAY, "IA_SERVICE_ERROR",
                "Erreur du service d'extraction : " + ex.getMessage());
    }

    // ── 500 — Erreur générique ────────────────────────────────────────────────
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntime(RuntimeException ex) {
        log.error("[500] Erreur inattendue : {}", ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Une erreur interne s'est produite. Contactez l'administrateur.");
    }

    // ── Builder ────────────────────────────────────────────────────────────────
    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String error, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status",    status.value());
        body.put("error",     error);
        body.put("message",   message);
        return ResponseEntity.status(status).body(body);
    }
}