package tn.rihab.projectservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.rihab.projectservice.model.DossierStatus;
import tn.rihab.projectservice.model.entity.Dossier;
import tn.rihab.projectservice.service.DossierService;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoints REST pour la gestion des dossiers.
 * Base URL : /api/dossiers
 *
 * Phase 1 — Dépôt :
 *   POST   /api/dossiers/upload          Upload AP + TDR → extraction P1
 *   GET    /api/dossiers                 Dashboard — liste triée
 *   GET    /api/dossiers/{id}            Détail d'un dossier
 *   GET    /api/dossiers/{id}/status     Polling statut (Angular 30s)
 *   PUT    /api/dossiers/{id}/priority   Override priorité manuelle
 *   GET    /api/dossiers/{id}/document-text  Texte brut (pour analyste-service)
 *
 * Le CORS est géré par l'API Gateway — PAS de @CrossOrigin ici.
 */
@RestController
@RequestMapping("/api/dossiers")
@RequiredArgsConstructor
@Slf4j
public class DossierController {

    private final DossierService dossierService;
    private final tn.rihab.projectservice.service.ValidationService validationService;

    // ── POST /api/dossiers/upload ─────────────────────────────────────────────

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Dossier> upload(
            @RequestPart("tdr") MultipartFile tdrFile,
            @RequestParam("dateLimite") String dateLimite,
            @RequestParam(value = "intitule", required = false) String intitule,
            @RequestParam(value = "isPrivate", defaultValue = "false") Boolean isPrivate) {

        log.info("[Upload] Dépôt du fichier : {} (isPrivate={})", tdrFile.getOriginalFilename(), isPrivate);
        validateFile(tdrFile);

        LocalDate dt = LocalDate.parse(dateLimite);

        // Récupérer l'email de l'utilisateur connecté via SecurityContext
        String createdByEmail = null;
        org.springframework.security.core.Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !authentication.getPrincipal().equals("anonymousUser")) {
            createdByEmail = authentication.getName(); // Assumant que getName() retourne l'email ou l'identifiant unique
        }

        // Cette méthode va maintenant :
        // 1. Enregistrer le dossier en base avec statut UPLOADED (et l'email de l'analyste)
        // 2. Lancer le parsing en arrière-plan via @Async
        // 3. Retourner le dossier immédiatement
        Dossier dossier = dossierService.createDossierWithAsyncParsing(tdrFile, dt, isPrivate, createdByEmail, intitule);

        return ResponseEntity.status(HttpStatus.CREATED).body(dossier);
    }
    @PostMapping("/{id}/analyze")
    public ResponseEntity<java.util.Map<String, String>> launchAnalysis(@PathVariable UUID id) {
        log.info("[Analyse] Lancement manuel pour le dossier ID : {}", id);

        dossierService.updateStatus(id, tn.rihab.projectservice.model.DossierStatus.PARSING_INITIAL);
        
        // Lance l'analyse IA dans un thread séparé pour ne pas bloquer la requête HTTP
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                dossierService.launchAnalysis(id);
            } catch (Exception e) {
                log.error("[Analyse] Erreur pendant l'analyse asynchrone : {}", e.getMessage(), e);
                dossierService.updateStatus(id, tn.rihab.projectservice.model.DossierStatus.ERROR);
            }
        });

        return ResponseEntity.ok(java.util.Map.of("message", "Analyse lancée avec succès"));
    }

    @PostMapping("/batch-analyze")
    public ResponseEntity<java.util.Map<String, String>> launchBatchAnalysis(@RequestBody List<UUID> ids) {
        log.info("[Analyse] Lancement par lot pour {} dossiers", ids.size());
        
        for(UUID id : ids) {
            dossierService.updateStatus(id, tn.rihab.projectservice.model.DossierStatus.PARSING_INITIAL);
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    dossierService.launchAnalysis(id);
                } catch (Exception e) {
                    log.error("[Analyse] Erreur pendant l'analyse asynchrone (lot) pour {}: {}", id, e.getMessage(), e);
                    dossierService.updateStatus(id, tn.rihab.projectservice.model.DossierStatus.ERROR);
                }
            });
        }

        return ResponseEntity.ok(java.util.Map.of("message", "Analyse par lot lancée avec succès pour " + ids.size() + " dossiers"));
    }

    @GetMapping
    public ResponseEntity<List<Dossier>> getAll() {
        return ResponseEntity.ok(dossierService.getAll());
    }

    // ── Notifications ──────────────────────────────────────────────────────────

    @GetMapping("/analyst/notifications/count")
    public ResponseEntity<Map<String, Integer>> getAnalystNotificationCount() {
        // Compter les dossiers en attente de validation
        long count = dossierService.getAll().stream()
                .filter(d -> d.getStatus() == tn.rihab.projectservice.model.DossierStatus.CORRECTION_LOOP
                          || d.getStatus() == tn.rihab.projectservice.model.DossierStatus.INDEXED)
                .count();
        return ResponseEntity.ok(Map.of("count", (int) count));
    }

    @GetMapping("/manager/notifications/count")
    public ResponseEntity<Map<String, Integer>> getManagerNotificationCount() {
        long count = dossierService.getAll().stream()
                .filter(d -> d.getStatus() == tn.rihab.projectservice.model.DossierStatus.PENDING_VALIDATION)
                .count();
        return ResponseEntity.ok(Map.of("count", (int) count));
    }

    // ── GET /api/dossiers/{id} ─────────────────────────────────────────────────

    @GetMapping("/{id}")
    public ResponseEntity<Dossier> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(dossierService.findById(id));
    }

    // ── GET /api/dossiers/{id}/status ──────────────────────────────────────────

    @GetMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable UUID id) {
        Dossier d = dossierService.findById(id);
        return ResponseEntity.ok(Map.of(
                "dossierId",      d.getId(),
                "status",         d.getStatus(),
                "pwinScore",      d.getPwinScore()      != null ? d.getPwinScore()      : "N/A",
                "joursOuvrables", d.getJoursOuvrables() != null ? d.getJoursOuvrables() : 0,
                "priorite",       d.getPriorite()       != null ? d.getPriorite()       : 2
        ));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable UUID id, @RequestParam tn.rihab.projectservice.model.DossierStatus status) {
        log.info("[DossierController] Mise à jour du statut pour le dossier {} : {}", id, status);
        dossierService.updateStatus(id, status);
        return ResponseEntity.ok().build();
    }

    // ── PUT /api/dossiers/{id}/priority ────────────────────────────────────────

    @PostMapping("/{id}/notify-analyst")
    public ResponseEntity<Void> notifyAnalyst(@PathVariable UUID id) {
        log.info("[DossierController] notifyAnalyst appele pour dossier {}", id);
        dossierService.updateStatus(id, tn.rihab.projectservice.model.DossierStatus.MATCHING);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/notify-manager-nogo")
    public ResponseEntity<Void> notifyManagerNoGo(@PathVariable UUID id) {
        validationService.sendNoGoTargeted(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/priority")
    public ResponseEntity<Dossier> updatePriority(
            @PathVariable UUID id,
            @RequestParam Integer priorite) {

        if (priorite < 1 || priorite > 3) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(dossierService.updatePriority(id, priorite));
    }

    // ── GET /api/dossiers/{id}/document-text ──────────────────────────────────

    @GetMapping("/{id}/document-text")
    public ResponseEntity<String> getDocumentText(@PathVariable UUID id) {
        return ResponseEntity.ok(dossierService.getDocumentText(id));
    }

    // ── GET /api/dossiers/manager/pending-nogo ───────────────────────────────
    
    @GetMapping("/manager/pending-nogo")
    public ResponseEntity<List<Dossier>> getPendingNoGo() {
        return ResponseEntity.ok(dossierService.getPendingNoGo());
    }

    // ── POST /api/dossiers/{id}/decision-nogo ────────────────────────────────
    
    @PostMapping("/{id}/decision-nogo")
    public ResponseEntity<Map<String, String>> processNoGoDecision(
            @PathVariable UUID id,
            @RequestBody tn.rihab.projectservice.dto.NoGoDecisionRequestDto request) {
        
        log.info("[Manager] Décision No-Go pour dossier {} : {}", id, request.getDecision());
        dossierService.processNoGoDecision(id, request);
        return ResponseEntity.ok(Map.of("message", "Décision enregistrée avec succès"));
    }

    // ── POST /api/dossiers/{id}/archive-and-audit ────────────────────────────
    
    @PostMapping("/{id}/archive-and-audit")
    public ResponseEntity<Map<String, String>> archiveAndAudit(@PathVariable UUID id) {
        log.info("[Manager] Action finale: Classer No-Go et Générer Audit pour dossier {}", id);
        dossierService.archiveAndAudit(id);
        return ResponseEntity.ok(Map.of("message", "Dossier archivé et audit généré"));
    }

    // ── Validation de fichier ──────────────────────────────────────────────────

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Fichier vide ou manquant");
        }
        String name = file.getOriginalFilename();
        if (name == null) {
            throw new IllegalArgumentException("Nom de fichier manquant");
        }
        String lower = name.toLowerCase();
        if (!lower.endsWith(".pdf") && !lower.endsWith(".docx") && !lower.endsWith(".doc")) {
            throw new IllegalArgumentException(
                    "Format non supporté : " + name + " — PDF ou DOCX uniquement");
        }
        if (file.getSize() > 100 * 1024 * 1024) {
            throw new IllegalArgumentException(
                    "Fichier trop volumineux : " + name + " — 100 Mo maximum");
        }
    }
}