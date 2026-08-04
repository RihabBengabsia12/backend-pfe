package tn.rihab.analysteservice.controller;

import tn.rihab.analysteservice.client.ProjectServiceClient;
import tn.rihab.analysteservice.model.AnalyseDossier;
import tn.rihab.analysteservice.model.MatchingResult;
import tn.rihab.analysteservice.model.PwinScore;
import tn.rihab.analysteservice.model.ScoringConfig;
import tn.rihab.analysteservice.repository.AnalyseDossierRepository;
import tn.rihab.analysteservice.repository.MatchingResultRepository;
import tn.rihab.analysteservice.repository.PwinScoreRepository;
import tn.rihab.analysteservice.scoring.ScoringConfigService;
import tn.rihab.analysteservice.scoring.ScoringEngine;
import tn.rihab.analysteservice.service.AnalyseDeepService;
import tn.rihab.analysteservice.service.NoGoReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoints Phase 2 — Scoring P-Win et décision Go/No-Go.
 * Base URL : /api/scoring
 *
 * POST /api/scoring/{id}/calculate   Calcule le score P-Win (5 axes)
 * GET  /api/scoring/{id}/result      Retourne le P-Win calculé
 * POST /api/scoring/{id}/force-go    Force le passage Go malgré No-Go
 * POST /api/scoring/{id}/confirm-nogo Confirme le No-Go définitivement
 * GET  /api/config/scoring           Retourne la configuration des seuils
 * PUT  /api/config/scoring           Met à jour les seuils et pondérations
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ScoringController {

    private final ScoringEngine              scoringEngine;
    private final ScoringConfigService       scoringConfigService;
    private final NoGoReportService          noGoReportService;
    private final PwinScoreRepository        pwinRepo;
    private final AnalyseDossierRepository   analyseRepo;
    private final MatchingResultRepository   matchingRepo;
    private final ProjectServiceClient       projectClient;
    private final tn.rihab.analysteservice.service.SystemAuditService systemAuditService;

    // ── POST /api/scoring/{id}/calculate ──────────────────────────────────────

    /**
     * Calcule le score P-Win sur les 5 axes et génère la décision automatique.
     *
     * Pré-requis : Phase 2 validée (AnalyseDossier avec les 10 risques renseignés).
     *
     * Décision automatique :
     *   P-Win >= seuilGoFort (70%)        → "GO"
     *   seuilGo (40%) <= P-Win < seuilGoFort → "GO_CONDITIONNEL"
     *   seuilNoGo (20%) <= P-Win < seuilGo  → "MANUAL" (intervention requise)
     *   P-Win < seuilNoGo (20%)           → "NO_GO"
     *   1 risque Rédhibitoire             → P-Win = 0 → "NO_GO" forcé
     *
     * @return PwinScore avec score global, décomposition par axe, décision
     */
    @PostMapping("/api/scoring/{id}/calculate")
    public ResponseEntity<PwinScore> calculate(@PathVariable UUID id) {
        log.info("[Scoring] Calcul P-Win — dossier {}", id);

        var dossier  = projectClient.getDossier(id);
        var analyse  = analyseRepo.findByDossierId(id)
                .orElseThrow(() -> new IllegalStateException(
                        "Phase 2 non complétée — lancer /api/analyses/" + id + "/deep-analysis d'abord"));
        var matching = matchingRepo.findByDossierId(id).orElse(null);

        PwinScore pwin = scoringEngine.calculate(dossier, analyse, matching);

        log.info("[Scoring] Dossier {} — P-Win={:.1f}% décision={}",
                id, pwin.getScoreGlobal(), pwin.getDecisionAuto());

        return ResponseEntity.ok(pwin);
    }

    // ── GET /api/scoring/{id}/result ──────────────────────────────────────────

    /**
     * Retourne le dernier P-Win calculé pour un dossier.
     * Utilisé par la page de scoring Angular (gauge, décomposition par axe,
     * décision colorée Go/No-Go).
     */
    @GetMapping("/api/scoring/{id}/result")
    public ResponseEntity<PwinScore> getResult(@PathVariable UUID id) {
        return ResponseEntity.ok(
                pwinRepo.findByDossierId(id)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Aucun score calculé pour le dossier : " + id)));
    }

    // ── POST /api/scoring/{id}/force-go ───────────────────────────────────────

    /**
     * Force le passage en Phase 3 malgré un score No-Go ou une intervention manuelle.
     *
     * Règles :
     *  - Justification obligatoire (min 50 mots)
     *  - Type de forçage obligatoire : STRATEGIQUE | PARTENARIAT_A_CONSOLIDER |
     *    CLIENT_PRIORITAIRE | AUTRE
     *  - Enregistré dans PwinScore avec horodatage (traçabilité audit)
     *  - Ne peut pas contourner un risque Rédhibitoire (protection absolue)
     *
     * Body JSON :
     * {
     *   "justification": "Cette mission est stratégique pour notre positionnement ...",
     *   "type": "STRATEGIQUE",
     *   "forcedBy": "Direction Générale"
     * }
     */
    @PostMapping("/api/scoring/{id}/force-go")
    public ResponseEntity<Map<String, Object>> forceGo(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {

        String justification = body.get("justification");
        String type          = body.get("type");
        String forcedBy      = body.get("forcedBy");

        // Validation de la justification (min 50 mots)
        if (justification == null || justification.trim().split("\\s+").length < 50) {
            throw new IllegalArgumentException(
                    "La justification du forçage doit contenir au moins 50 mots. " +
                            "Reçu : " + (justification != null ? justification.trim().split("\\s+").length : 0) + " mots");
        }

        // Validation du type
        var typesAutorises = java.util.Set.of(
                "STRATEGIQUE", "PARTENARIAT_A_CONSOLIDER", "CLIENT_PRIORITAIRE", "AUTRE");
        if (type == null || !typesAutorises.contains(type.toUpperCase())) {
            throw new IllegalArgumentException(
                    "Type de forçage invalide. Valeurs acceptées : " + typesAutorises);
        }

        // Récupérer le score P-Win
        PwinScore pwin = pwinRepo.findByDossierId(id)
                .orElseThrow(() -> new IllegalStateException(
                        "Aucun score calculé — calculer le P-Win avant de forcer"));

        // Bloquer si risque Rédhibitoire (règle absolue non contournable)
        if (Boolean.TRUE.equals(pwin.getRisqueRedhibitoire())) {
            throw new IllegalStateException(
                    "Forçage Go impossible : un risque Rédhibitoire a été détecté sur [[" +
                            pwin.getRisqueRedhibitoireChamp() + "]]. " +
                            "Ce type de risque ne peut pas être contourné par un forçage. " +
                            "Résoudre le risque d'abord (corriger le niveau de risque si l'évaluation était erronée).");
        }

        // Enregistrer le forçage
        pwin.setForceGo(true);
        pwin.setForceGoJustif(justification);
        pwin.setForceGoType(type.toUpperCase());
        pwin.setForceGoBy(forcedBy);
        pwin.setForceGoAt(LocalDateTime.now());
        pwinRepo.save(pwin);

        log.warn("[Scoring] FORCE_GO — dossier {} par {} (type={} P-Win={}%)",
                id, forcedBy, type, pwin.getScoreGlobal());

        try {
            projectClient.notifyAnalyst(id);
        } catch (Exception e) {
            log.error("[Scoring] Failed to notify analyst for dossier {}", id, e);
        }

        return ResponseEntity.ok(Map.of(
                "status",      "FORCE_GO_ENREGISTRÉ",
                "dossierId",   id.toString(),
                "pwinScore",   pwin.getScoreGlobal(),
                "type",        type.toUpperCase(),
                "message",     "Forçage Go enregistré. Le pipeline Phase 3 va démarrer.",
                "avertissement","Ce forçage est enregistré dans l'audit trail et visible dans le rapport final."
        ));
    }

    // ── POST /api/scoring/{id}/confirm-nogo ───────────────────────────────────

    /**
     * Confirme le No-Go définitivement.
     * Génère le rapport No-Go DOCX et clôture le dossier (NO_GO_CONFIRMED).
     *
     * Body JSON (optionnel) :
     * {
     *   "commentaire": "Délai insuffisant et budget trop faible pour être rentable"
     * }
     */
    @PostMapping("/api/scoring/{id}/confirm-nogo")
    public ResponseEntity<Map<String, Object>> confirmNoGo(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body) {

        log.info("[Scoring] Confirmation No-Go — dossier {}", id);

        var dossier = projectClient.getDossier(id);
        var pwin    = pwinRepo.findByDossierId(id)
                .orElseThrow(() -> new IllegalStateException(
                        "Aucun score P-Win trouvé pour ce dossier"));
        var analyse = analyseRepo.findByDossierId(id)
                .orElseThrow(() -> new IllegalStateException(
                        "Analyse Phase 2 non trouvée pour ce dossier"));

        // Générer le rapport No-Go DOCX
        var rapport = noGoReportService.generate(id, dossier, pwin, analyse);

        // URL de téléchargement du rapport
        String docxPath = rapport.getDocxPath();

        return ResponseEntity.ok(Map.of(
                "status",       "NO_GO_CONFIRMED",
                "dossierId",    id.toString(),
                "pwinScore",    pwin.getScoreGlobal(),
                "motifPrincipal", pwin.getMotifNogo() != null ? pwin.getMotifNogo() : "",
                "rapportPath",  docxPath != null ? docxPath : "",
                "message",      "No-Go confirmé. Rapport généré et disponible en téléchargement."
        ));
    }

    // ── GET /api/config/scoring ────────────────────────────────────────────────

    /**
     * Retourne la configuration actuelle des seuils et pondérations P-Win.
     * Accessible uniquement par les rôles ADMIN.
     */
    @GetMapping("/api/config/scoring")
    public ResponseEntity<ScoringConfig> getScoringConfig() {
        return ResponseEntity.ok(scoringConfigService.getCurrent());
    }

    // ── PUT /api/config/scoring ────────────────────────────────────────────────

    /**
     * Met à jour les seuils et pondérations du moteur P-Win.
     * Effectif immédiatement sans redémarrage.
     *
     * Contraintes validées :
     *  - poidsA + poidsB + poidsC + poidsD + poidsE = 1.0 (±0.01)
     *  - seuilNoGo < seuilGoConditionnel < seuilGoFort
     *  - tjmMinEgis < tjmMaxEgis
     *
     * Body JSON :
     * {
     *   "seuilNoGo": 20.0,
     *   "seuilGoConditionnel": 40.0,
     *   "seuilGoFort": 70.0,
     *   "poidsA_faisabilite": 0.25,
     *   "poidsB_rentabilite": 0.25,
     *   "poidsC_risques": 0.25,
     *   "poidsD_concurrence": 0.15,
     *   "poidsE_conformite": 0.10,
     *   "tjmMinEgis": 350.0,
     *   "tjmMaxEgis": 3000.0
     * }
     */
    @PutMapping("/api/config/scoring")
    public ResponseEntity<ScoringConfig> updateScoringConfig(
            @RequestBody ScoringConfig updated) {

        log.info("[Config] Mise à jour scoring — seuilNoGo={} seuilGo={} seuilGoFort={}",
                updated.getSeuilNoGo(), updated.getSeuilGoConditionnel(), updated.getSeuilGoFort());
        
        ScoringConfig saved = scoringConfigService.update(updated);
        
        // Traçabilité Globale (Niveau Système)
        String detail = String.format("Mise à jour des seuils : NoGo=%.1f, GoCond=%.1f, GoFort=%.1f",
                updated.getSeuilNoGo(), updated.getSeuilGoConditionnel(), updated.getSeuilGoFort());
        systemAuditService.logSystemAction("admin@st2i.com.tn", "UPDATE_SCORING_THRESHOLDS", detail);

        return ResponseEntity.ok(saved);
    }
}