package tn.rihab.analysteservice.controller;

import tn.rihab.analysteservice.dto.DossierDto;
import tn.rihab.analysteservice.client.ProjectServiceClient;
import tn.rihab.analysteservice.dto.ia.ChecklistResponseDto;
import tn.rihab.analysteservice.dto.ia.MethodologieResponseDto;
import tn.rihab.analysteservice.model.*;
import tn.rihab.analysteservice.repository.AnalyseDossierRepository;
import tn.rihab.analysteservice.repository.MatchingResultRepository;
import tn.rihab.analysteservice.repository.NoGoReportRepository;
import tn.rihab.analysteservice.repository.PwinScoreRepository;
import tn.rihab.analysteservice.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Endpoints Phase 4 — Export des documents générés.
 * Base URL : /api/export
 *
 * POST /api/export/{id}/apo-docx       Génère/régénère l'APO DOCX
 * POST /api/export/{id}/methodologie   Génère/régénère la méthodologie DOCX
 * POST /api/export/{id}/pack-zip       Génère/régénère le pack ZIP complet
 * POST /api/export/{id}/nogo-report    Génère/régénère le rapport No-Go DOCX
 *
 * Chaque endpoint retourne le chemin MinIO du document généré.
 * Le téléchargement effectif se fait via project-service
 * (GET /api/dossiers/{id}/download/{type} avec URL présignée).
 */
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
@Slf4j
public class ExportController {

    private final DocumentExportService    exportService;
    private final PackGeneratorService     packGeneratorService;
    private final ChecklistService         checklistService;
    private final NoGoReportService        noGoReportService;
    private final ProjectServiceClient     projectClient;
    private final ApoAssemblyService       apoAssemblyService;
    private final AnalyseDossierRepository analyseRepo;
    private final MatchingResultRepository matchingRepo;
    private final PwinScoreRepository      pwinRepo;
    private final NoGoReportRepository     noGoRepo;
    private final PackStorageService       packStorageService;

    // ── POST /api/export/{id}/apo-docx ────────────────────────────────────────

    /**
     * Régénère uniquement l'APO DOCX (56 placeholders) depuis l'ApoData actuel.
     * Utile après une correction manuelle d'un champ via PUT /api/apo/{id}/field/{name}.
     *
     * @return { "path": "apo-generees/uuid/APO_xxx.docx" }
     */
    @PostMapping("/{id}/apo-docx")
    public ResponseEntity<Map<String, String>> exportApoDocx(@PathVariable UUID id) {
        log.info("[Export] Régénération APO DOCX — dossier {}", id);

        ApoData apoData     = apoAssemblyService.getApoData(id);
        DossierDto dossier  = projectClient.getDossier(id);

        String path = exportService.exportApo(apoData, dossier.getIntituleOffre());

        return ResponseEntity.ok(Map.of(
                "status", "GENERATED",
                "path",   path,
                "type",   "apo"
        ));
    }

    // ── GET /api/export/{id}/apo-docx/download ──────────────────────────────
    @GetMapping("/{id}/apo-docx/download")
    public ResponseEntity<byte[]> downloadApoDocx(@PathVariable UUID id) {
        log.info("[Export] Téléchargement (Preview) APO DOCX - dossier {}", id);
        
        try {
            ApoData apoData = apoAssemblyService.getApoData(id);
            DossierDto dossier = projectClient.getDossier(id);
            String path = exportService.exportApo(apoData, dossier.getIntituleOffre());
            
            byte[] bytes = packStorageService.downloadBytes(path);
            return ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"APO_Complet.docx\"")
                    .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .body(bytes);
        } catch (Exception e) {
            log.error("[Export] Erreur téléchargement APO DOCX", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── POST /api/export/{id}/methodologie ────────────────────────────────────

    /**
     * Régénère la méthodologie DOCX (5 sections) en relançant la génération Claude.
     * Utile si l'analyste veut une nouvelle version après affinement du matching.
     */
    @PostMapping("/{id}/methodologie")
    public ResponseEntity<byte[]> exportMethodologie(@PathVariable UUID id) {
        log.info("[Export] Régénération méthodologie — dossier {}", id);

        DossierDto dossier = projectClient.getDossier(id);
        AnalyseDossier analyse = analyseRepo.findByDossierId(id)
                .orElseThrow(() -> new IllegalStateException("Phase 2 non complétée"));
        MatchingResult matching = matchingRepo.findByDossierId(id).orElse(null);

        ApoData apoData = apoAssemblyService.getApoData(id);

        if (apoData.getChamps().isEmpty()) {
            throw new IllegalStateException(
                    "Aucune donnée APO disponible — lancer POST /api/apo/" + id + "/assemble d'abord");
        }

        String path = apoAssemblyService.regenerateMethodologie(dossier, analyse, matching, apoData);
        byte[] bytes = packStorageService.downloadBytes(path);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"Methodologie_ProjectIQ.docx\"")
                .contentType(org.springframework.http.MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(bytes);
    }

    // ── POST /api/export/{id}/pack-zip ────────────────────────────────────────

    /**
     * Régénère le pack ZIP complet de soumission.
     * Recompile : APO DOCX + Méthodologie DOCX + Rapport DOCX + Checklist + README.
     *
     * Pré-requis : les 3 documents (apo, methodo, rapport) doivent déjà exister.
     * Utilise les chemins MinIO actuellement stockés dans Dossier (project-service).
     */
    /** Télécharge la méthodologie déjà générée, sans relancer Claude. */
    @GetMapping("/{id}/methodologie/download")
    public ResponseEntity<byte[]> downloadMethodologie(@PathVariable UUID id) {
        try {
            DossierDto dossier = projectClient.getDossier(id);
            if (dossier.getMethodoDocxPath() == null || dossier.getMethodoDocxPath().isBlank()) {
                return ResponseEntity.notFound().build();
            }
            byte[] bytes = packStorageService.downloadBytes(dossier.getMethodoDocxPath());
            return ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"Methodologie_ProjectIQ.docx\"")
                    .contentType(org.springframework.http.MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .body(bytes);
        } catch (Exception e) {
            log.error("[Export] Erreur téléchargement méthodologie pour {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{id}/pack-zip")
    public ResponseEntity<Map<String, String>> exportPackZip(@PathVariable UUID id) {
        log.info("[Export] Régénération pack ZIP — dossier {}", id);

        DossierDto dossier = projectClient.getDossier(id);

        if (dossier.getApoDocxPath() == null) {
            throw new IllegalStateException(
                    "APO DOCX non disponible — générer l'APO avant le pack (POST /api/apo/" + id + "/assemble)");
        }

        ChecklistResponseDto checklist = checklistService.generate(
                dossier.getBailleurs(), dossier.getPays());

        String packPath = packGeneratorService.generatePack(
                id,
                dossier.getApoDocxPath(),
                dossier.getMethodoDocxPath(),
                dossier.getRapportPath(),
                checklist,
                dossier.getIntituleOffre());

        return ResponseEntity.ok(Map.of(
                "status", "GENERATED",
                "path",   packPath,
                "type",   "pack-zip"
        ));
    }

    // ── POST /api/export/{id}/nogo-report ─────────────────────────────────────

    /**
     * Régénère le rapport No-Go DOCX.
     * Appelé typiquement depuis POST /api/scoring/{id}/confirm-nogo,
     * mais accessible aussi directement pour régénération.
     */
    @PostMapping("/{id}/nogo-report")
    public ResponseEntity<Map<String, String>> exportNoGoReport(@PathVariable UUID id, @RequestBody(required = false) Map<String, String> body) {
        log.info("[Export] Régénération rapport No-Go — dossier {}", id);

        DossierDto dossier = projectClient.getDossier(id);
        PwinScore pwin = pwinRepo.findByDossierId(id)
                .orElseThrow(() -> new IllegalStateException("P-Win non calculé"));
        AnalyseDossier analyse = analyseRepo.findByDossierId(id)
                .orElseThrow(() -> new IllegalStateException("Phase 2 non complétée"));

        String analysteName = (body != null && body.containsKey("analysteName")) ? body.get("analysteName") : "Système Expert IA";
        NoGoReport rapport = noGoReportService.generate(id, dossier, pwin, analyse, analysteName);

        return ResponseEntity.ok(Map.of(
                "status", "GENERATED",
                "path",   rapport.getDocxPath() != null ? rapport.getDocxPath() : "",
                "type",   "nogo-report"
        ));
    }

    // ▪▪ RAPPORT GÉnÉRAL (PREVIEW DIRECT) ▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪▪

    @GetMapping("/templates/{templateName}")
    public ResponseEntity<org.springframework.core.io.Resource> downloadTemplate(@PathVariable String templateName) {
        log.info("[Export] Téléchargement du template : {}", templateName);
        try {
            org.springframework.core.io.Resource resource = new org.springframework.core.io.ClassPathResource("templates/" + templateName);
            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + templateName + "\"")
                    .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .body(resource);
        } catch (Exception e) {
            log.error("[Export] Erreur lors du téléchargement du template {}", templateName, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}/general-report/download")
    public ResponseEntity<byte[]> downloadGeneralReport(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean force) {
        log.info("[Export] Téléchargement Rapport Général (via ApoData) - dossier {}", id);
        
        try {
            DossierDto dossier = projectClient.getDossier(id);
            PwinScore pwin = pwinRepo.findByDossierId(id).orElse(null);
            
            tn.rihab.analysteservice.model.ApoData apoData = apoAssemblyService.getApoData(id);
            
            // Génère (ou regénère) le Rapport Général depuis les données APO
            String path = exportService.exportRapportResultat(apoData, pwin, dossier.getIntituleOffre());
            byte[] docxBytes = packStorageService.downloadBytes(path);
            
            return ResponseEntity.ok()
                    .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Rapport_General.docx\"")
                    .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .body(docxBytes);
        } catch (Exception e) {
            log.error("[Export] Erreur téléchargement Rapport Général", e);
            return ResponseEntity.status(500).build();
        }
    }

    // ── GET /api/export/{id}/pack-nogo/download ───────────────────────────────

    /**
     * Génère le pack ZIP NO-GO complet en une seule passe :
     *   1. Génère le Rapport Général DOCX (avec textes Claude)
     *   2. Génère/récupère l'APO DOCX complet
     *   3. Zippe les deux et retourne le ZIP en réponse HTTP.
     *
     * Utilisé par le frontend pour le téléchargement automatique après génération du rapport.
     */
    @GetMapping("/{id}/pack-nogo/download")
    public ResponseEntity<byte[]> downloadPackNogo(@PathVariable UUID id) {
        log.info("[Export] Génération Pack NO-GO (Rapport + APO) — dossier {}", id);

        DossierDto dossier = projectClient.getDossier(id);
        MatchingResult matching = matchingRepo.findByDossierId(id).orElse(null);
        tn.rihab.analysteservice.model.AnalyseDossier analyse = analyseRepo.findByDossierId(id).orElse(null);
        tn.rihab.analysteservice.model.PwinScore pwin = pwinRepo.findByDossierId(id).orElse(null);

        // 1. Rapport Général DOCX (généré à partir de ApoData)
        byte[] rapportBytes = null;
        try {
            tn.rihab.analysteservice.model.ApoData apoData = apoAssemblyService.getApoData(id);
            String rapportPath = exportService.exportRapportResultat(apoData, pwin, dossier.getIntituleOffre());
            rapportBytes = packStorageService.downloadBytes(rapportPath);
        } catch (Exception e) {
            log.warn("[Export] Pack NO-GO — rapport non généré : {}", e.getMessage());
            // Fallback vide si erreur
            rapportBytes = new byte[0];
        }

        // 2. APO DOCX (depuis l'ApoData en base)
        byte[] apoBytes = null;
        try {
            tn.rihab.analysteservice.model.ApoData apoData = apoAssemblyService.getApoData(id);
            String apoPath = exportService.exportApo(apoData, dossier.getIntituleOffre());
            apoBytes = packStorageService.downloadBytes(apoPath);
        } catch (Exception e) {
            log.warn("[Export] Pack NO-GO — APO DOCX non disponible : {}", e.getMessage());
        }

        // 4. Création du ZIP en mémoire
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        String safeName = dossier.getIntituleOffre() != null
                ? dossier.getIntituleOffre().replaceAll("[^a-zA-Z0-9_\\-]", "_").substring(0, Math.min(50, dossier.getIntituleOffre().length()))
                : id.toString().substring(0, 8);

        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
            // Rapport
            zos.putNextEntry(new java.util.zip.ZipEntry("Rapport_General_" + safeName + ".docx"));
            zos.write(rapportBytes);
            zos.closeEntry();

            // APO (si disponible)
            if (apoBytes != null && apoBytes.length > 0) {
                zos.putNextEntry(new java.util.zip.ZipEntry("APO_" + safeName + ".docx"));
                zos.write(apoBytes);
                zos.closeEntry();
            }

            // README
            String readme = "Pack NO-GO — " + dossier.getIntituleOffre() + "\n"
                    + "Généré le : " + java.time.LocalDate.now() + "\n\n"
                    + "Contenu :\n"
                    + "  - Rapport_General_" + safeName + ".docx  →  Rapport d'analyse et décision NO-GO\n"
                    + (apoBytes != null ? "  - APO_" + safeName + ".docx            →  Formulaire APO complet\n" : "")
                    + "\nDocument confidentiel — Projet IQ";
            zos.putNextEntry(new java.util.zip.ZipEntry("README.txt"));
            zos.write(readme.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zos.closeEntry();

        } catch (java.io.IOException e) {
            log.error("[Export] Erreur ZIP : {}", e.getMessage());
            throw new RuntimeException("Erreur lors de la création du pack ZIP", e);
        }

        byte[] zipBytes = baos.toByteArray();
        log.info("[Export] Pack NO-GO généré : {} bytes", zipBytes.length);

        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"Pack_NOGO_" + safeName + ".zip\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("application/zip"))
                .body(zipBytes);
    }
}
