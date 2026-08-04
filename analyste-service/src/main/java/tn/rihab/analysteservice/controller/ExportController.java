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

    // ── POST /api/export/{id}/methodologie ────────────────────────────────────

    /**
     * Régénère la méthodologie DOCX (5 sections) en relançant la génération Claude.
     * Utile si l'analyste veut une nouvelle version après affinement du matching.
     */
    @PostMapping("/{id}/methodologie")
    public ResponseEntity<Map<String, String>> exportMethodologie(@PathVariable UUID id) {
        log.info("[Export] Régénération méthodologie — dossier {}", id);

        DossierDto dossier = projectClient.getDossier(id);
        AnalyseDossier analyse = analyseRepo.findByDossierId(id)
                .orElseThrow(() -> new IllegalStateException("Phase 2 non complétée"));
        MatchingResult matching = matchingRepo.findByDossierId(id).orElse(null);

        // Note : la génération du contenu passe par ApoAssemblyService en interne
        // via assembleAndGenerate(). Ici on déclenche un export simple si déjà généré.
        ApoData apoData = apoAssemblyService.getApoData(id);

        if (apoData.getChamps().isEmpty()) {
            throw new IllegalStateException(
                    "Aucune donnée APO disponible — lancer POST /api/apo/" + id + "/assemble d'abord");
        }

        // Re-déclencher l'assemblage complet (regénère les 3 docs + pack)
        PwinScore pwin = pwinRepo.findByDossierId(id)
                .orElseThrow(() -> new IllegalStateException("P-Win non calculé"));

        apoAssemblyService.assembleAndGenerate(id, dossier, analyse, matching, pwin);

        return ResponseEntity.accepted().body(Map.of(
                "status",  "REGENERATION_STARTED",
                "message", "Méthodologie en cours de régénération (incluse dans le pipeline complet)"
        ));
    }

    // ── POST /api/export/{id}/pack-zip ────────────────────────────────────────

    /**
     * Régénère le pack ZIP complet de soumission.
     * Recompile : APO DOCX + Méthodologie DOCX + Rapport DOCX + Checklist + README.
     *
     * Pré-requis : les 3 documents (apo, methodo, rapport) doivent déjà exister.
     * Utilise les chemins MinIO actuellement stockés dans Dossier (project-service).
     */
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
    public ResponseEntity<Map<String, String>> exportNoGoReport(@PathVariable UUID id) {
        log.info("[Export] Régénération rapport No-Go — dossier {}", id);

        DossierDto dossier = projectClient.getDossier(id);
        PwinScore pwin = pwinRepo.findByDossierId(id)
                .orElseThrow(() -> new IllegalStateException("P-Win non calculé"));
        AnalyseDossier analyse = analyseRepo.findByDossierId(id)
                .orElseThrow(() -> new IllegalStateException("Phase 2 non complétée"));

        NoGoReport rapport = noGoReportService.generate(id, dossier, pwin, analyse);

        return ResponseEntity.ok(Map.of(
                "status", "GENERATED",
                "path",   rapport.getDocxPath() != null ? rapport.getDocxPath() : "",
                "type",   "nogo-report"
        ));
    }

    // ── GET /api/export/templates/{templateName} ──────────────────────────────

    @GetMapping("/templates/{templateName:.+}")
    public ResponseEntity<org.springframework.core.io.Resource> getTemplate(@PathVariable String templateName) {
        org.springframework.core.io.Resource resource = new org.springframework.core.io.ClassPathResource("templates/" + templateName);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + templateName + "\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(resource);
    }
}