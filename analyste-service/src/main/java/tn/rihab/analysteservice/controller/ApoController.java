package tn.rihab.analysteservice.controller;

import tn.rihab.analysteservice.dto.DossierDto;
import tn.rihab.analysteservice.client.ProjectServiceClient;
import tn.rihab.analysteservice.model.AnalyseDossier;
import tn.rihab.analysteservice.model.ApoData;
import tn.rihab.analysteservice.model.MatchingResult;
import tn.rihab.analysteservice.model.PwinScore;
import tn.rihab.analysteservice.repository.AnalyseDossierRepository;
import tn.rihab.analysteservice.repository.MatchingResultRepository;
import tn.rihab.analysteservice.repository.PwinScoreRepository;
import tn.rihab.analysteservice.service.ApoAssemblyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Endpoints Phase 4 — Assemblage et édition de l'APO.
 * Base URL : /api/apo
 *
 * POST /api/apo/{id}/assemble       Assemble les 65 champs + génère les 3 documents
 * GET  /api/apo/{id}                Retourne le dictionnaire complet ApoData
 * PUT  /api/apo/{id}/field/{name}   Modifie un champ spécifique (édition manuelle)
 * GET  /api/apo/{id}/completeness   Pourcentage de complétude de l'APO
 */
@RestController
@RequestMapping("/api/apo")
@RequiredArgsConstructor
@Slf4j
public class ApoController {

    private final ApoAssemblyService       apoAssemblyService;
    private final ProjectServiceClient     projectClient;
    private final AnalyseDossierRepository analyseRepo;
    private final MatchingResultRepository matchingRepo;
    private final PwinScoreRepository      pwinRepo;

    // ── POST /api/apo/{id}/assemble ───────────────────────────────────────────

    /**
     * Déclenche manuellement l'assemblage complet de l'APO et la génération
     * des 3 documents (APO DOCX, Méthodologie DOCX, Rapport DOCX) + pack ZIP.
     *
     * En fonctionnement normal, déclenché automatiquement par AnalyseDeepService
     * après la Phase 3 (matching). Cet endpoint permet un re-déclenchement manuel
     * depuis Angular (ex: après correction d'un champ Phase 2/3).
     *
     * Pré-requis : Phase 2 (scoring) et Phase 3 (matching) doivent être complétées.
     */
    @PostMapping("/{id}/assemble")
    public ResponseEntity<Map<String, Object>> assemble(@PathVariable UUID id) {
        log.info("[ApoController] Assemblage manuel — dossier {}", id);

        DossierDto dossier = projectClient.getDossier(id);

        AnalyseDossier analyse = analyseRepo.findByDossierId(id)
                .orElseThrow(() -> new IllegalStateException(
                        "Phase 2 non complétée — impossible d'assembler l'APO"));

        MatchingResult matching = matchingRepo.findByDossierId(id).orElse(null);

        PwinScore pwin = pwinRepo.findByDossierId(id)
                .orElseThrow(() -> new IllegalStateException(
                        "Aucun score P-Win calculé — impossible d'assembler l'APO"));

        apoAssemblyService.assembleAndGenerate(id, dossier, analyse, matching, pwin);

        return ResponseEntity.accepted().body(Map.of(
                "status",  "ASSEMBLY_STARTED",
                "message", "Assemblage de l'APO et génération des documents en cours",
                "dossierId", id.toString()
        ));
    }

    // ── GET /api/apo/{id} ──────────────────────────────────────────────────────

    /**
     * Retourne le dictionnaire complet de l'APO (65 champs).
     *
     * Chaque champ contient : { valeur, statut, source }
     * statut ∈ { AUTO, CLAUDE, MANUAL, CALCULATED, EMPTY }
     *
     * Utilisé par l'éditeur APO Angular pour afficher chaque champ
     * avec sa couleur selon le statut (vert=AUTO, bleu=CLAUDE, orange=MANUAL/EMPTY).
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApoData> getApoData(@PathVariable UUID id) {
        return ResponseEntity.ok(apoAssemblyService.getApoData(id));
    }

    // ── PUT /api/apo/{id}/field/{name} ────────────────────────────────────────

    /**
     * Modifie un champ spécifique de l'APO (édition manuelle dans Angular).
     * Marque automatiquement le champ comme statut "MANUAL" / source "user".
     *
     * Utilisé notamment pour compléter les champs obligatoires non remplis
     * par Claude : BUDGET_INTERNE, PLAN_ACTION, PARTENAIRES, CHEF_DE_FILE,
     * ROLES_REPARTITION, SHORTLIST, SHORTLIST_EQUILIBREE, CAPACITE_DELAI, etc.
     *
     * Body JSON : { "valeur": "Texte de la nouvelle valeur du champ" }
     */
    @PutMapping("/{id}/field/{name}")
    public ResponseEntity<ApoData> updateField(
            @PathVariable UUID id,
            @PathVariable String name,
            @RequestBody Map<String, String> body) {

        String valeur = body.get("valeur");
        if (valeur == null) {
            throw new IllegalArgumentException("Le champ 'valeur' est requis dans le body");
        }

        log.info("[ApoController] Modification champ [[{}]] — dossier {}", name, id);

        return ResponseEntity.ok(apoAssemblyService.updateField(id, name, valeur));
    }

    // ── GET /api/apo/{id}/completeness ────────────────────────────────────────

    /**
     * Retourne le pourcentage de complétude de l'APO et le détail
     * des champs manquants par catégorie (statut EMPTY ou MANUAL non rempli).
     *
     * Utilisé pour la barre de progression dans l'interface Angular
     * et pour bloquer le passage à PACK_READY si des champs critiques manquent.
     */
    @GetMapping("/{id}/completeness")
    public ResponseEntity<Map<String, Object>> getCompleteness(@PathVariable UUID id) {
        ApoData apoData = apoAssemblyService.getApoData(id);

        long total   = apoData.getChamps().size();
        long remplis = apoData.getChamps().values().stream()
                .filter(c -> c.getValeur() != null && !c.getValeur().isBlank())
                .count();

        var champsManquants = apoData.getChamps().entrySet().stream()
                .filter(e -> e.getValue().getValeur() == null || e.getValue().getValeur().isBlank())
                .map(Map.Entry::getKey)
                .toList();

        var champsManuelsManquants = apoData.getChamps().entrySet().stream()
                .filter(e -> "MANUAL".equals(e.getValue().getStatut())
                        && (e.getValue().getValeur() == null || e.getValue().getValeur().isBlank()))
                .map(Map.Entry::getKey)
                .toList();

        return ResponseEntity.ok(Map.of(
                "dossierId",              id,
                "completeness",           apoData.getCompleteness() != null ? apoData.getCompleteness() : 0.0,
                "totalChamps",            total,
                "champsRemplis",          remplis,
                "champsManquants",        champsManquants,
                "champsManuelsManquants", champsManuelsManquants,
                "pretPourExport",         champsManuelsManquants.isEmpty()
        ));
    }
}