package tn.rihab.analysteservice.controller;

import tn.rihab.analysteservice.client.ProjectServiceClient;
import tn.rihab.analysteservice.dto.ia.RequirementsResponseDto;
import tn.rihab.analysteservice.matching.MatchingEngine;
import tn.rihab.analysteservice.matching.matchers.CompetencesMatcher;
import tn.rihab.analysteservice.matching.matchers.ExpertsMatcher;
import tn.rihab.analysteservice.matching.matchers.ReferencesMatcher;
import tn.rihab.analysteservice.messaging.AnalysteEventPublisher;
import tn.rihab.analysteservice.model.MatchingResult;
import tn.rihab.analysteservice.repository.MatchingResultRepository;
import tn.rihab.analysteservice.repository.AnalyseDossierRepository;
import tn.rihab.analysteservice.scoring.ScoringConfigService;
import tn.rihab.analysteservice.scoring.ScoringEngine;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoints Phase 3 — Matching avec le référentiel société.
 * Base URL : /api/matching
 *
 * POST /api/matching/{id}/run               Lance le matching complet
 * GET  /api/matching/{id}/result             Retourne le résultat du matching
 * GET  /api/matching/{id}/matrix             Retourne la matrice de différenciation
 * POST /api/matching/{id}/force-compatible   Force la compatibilité malgré un gap
 */
@RestController
@RequestMapping("/api/matching")
@RequiredArgsConstructor
@Slf4j
public class MatchingController {

    private final MatchingEngine           matchingEngine;
    private final MatchingResultRepository matchingRepo;
    private final ProjectServiceClient     projectClient;
    private final AnalysteEventPublisher   analysteEventPublisher;
    private final CompetencesMatcher       competencesMatcher;
    private final ExpertsMatcher           expertsMatcher;
    private final ReferencesMatcher        referencesMatcher;
    private final ScoringConfigService     scoringConfigService;
    private final ObjectMapper             objectMapper;
    private final AnalyseDossierRepository analyseDossierRepository;
    private final ScoringEngine            scoringEngine;

    // ── POST /api/matching/{id}/run ───────────────────────────────────────────

    /**
     * Lance le matching complet Phase 3 pour un dossier.
     *
     * Étapes exécutées :
     *  1. ia-service extrait les exigences structurées de la DP (refs, qualifs, experts)
     *  2. CompetencesMatcher  → tauxCouvertureCompetences (0.0–1.0)
     *  3. ReferencesMatcher   → GAP_REFS + références disponibles Egis
     *  4. ExpertsMatcher      → tauxCouvertureExperts + experts identifiés
     *  5. ClientMatcher       → RELATION_CLIENT niveau 1-5
     *  6. ia-service génère   → matrice de différenciation (critère | position | argument)
     *
     * En fonctionnement normal, déclenché automatiquement par AnalyseDeepService.
     * Cet endpoint permet un re-déclenchement manuel depuis Angular.
     *
     * @return MatchingResult complet persisté
     */
    @PostMapping("/{id}/run")
    public ResponseEntity<MatchingResult> runMatching(@PathVariable UUID id) {
        log.info("[Matching] Déclenchement Phase 3 — dossier {}", id);

        var dossier      = projectClient.getDossier(id);
        var documentText = projectClient.getDocumentText(id);

        MatchingResult result = matchingEngine.runMatching(dossier, documentText);

        log.info("[Matching] Dossier {} — compétences={}% experts={}% client=niv{}",
                id,
                result.getTauxCouvertureCompetences() != null
                        ? String.format("%.0f", result.getTauxCouvertureCompetences() * 100) : "0",
                result.getTauxCouvertureExperts() != null
                        ? String.format("%.0f", result.getTauxCouvertureExperts() * 100) : "0",
                result.getRelationClientNiveau());

        return ResponseEntity.ok(result);
    }

    // ── POST /api/matching/{id}/recalculate ──────────────────────────────────
    /**
     * Recalcule uniquement les taux (compétences, experts, références)
     * en réutilisant les exigences DÉJÀ EXTRAITES et stockées en base.
     * N'appelle PAS Claude — fonctionne même si le quota API est épuisé.
     */
    @PostMapping("/{id}/recalculate")
    public ResponseEntity<MatchingResult> recalculateMatching(@PathVariable UUID id) {
        log.info("[Matching] Recalcul sans IA — dossier {}", id);

        MatchingResult existing = matchingRepo.findByDossierId(id)
                .orElseThrow(() -> new IllegalArgumentException("Matching introuvable. Lancez d'abord /run."));

        var dossier = projectClient.getDossier(id);

        try {
            // 1. Réutiliser les exigences déjà extraites (stockées en JSON)
            List<String> qualifs = objectMapper.readValue(
                    existing.getQualifsExigees() != null ? existing.getQualifsExigees() : "[]",
                    new TypeReference<List<String>>() {});

            List<RequirementsResponseDto.ExpertRequisDto> expertsRequisList = objectMapper.readValue(
                    existing.getExpertsRequis() != null ? existing.getExpertsRequis() : "[]",
                    new TypeReference<List<RequirementsResponseDto.ExpertRequisDto>>() {});

            // 2. Recalculer compétences avec le référentiel mis à jour
            var competencesResult = competencesMatcher.match(qualifs);

            // 3. Recalculer références
            Double budget = null;
            try { budget = Double.parseDouble(dossier.getBudgetGlobal()); } catch (Exception ignored) {}
            var referencesResult = referencesMatcher.match(existing.getSecteurAo(), dossier.getPays(), budget);

            // 4. Recalculer experts
            var expertsResult = expertsMatcher.match(expertsRequisList, dossier);

            // 5. Mise à jour des taux uniquement (la matrice Claude reste inchangée)
            existing.setTauxCouvertureCompetences(competencesResult.taux());
            existing.setCompetencesDetail(competencesResult.detailJson());
            existing.setGapRefs(referencesResult.gapRefsJson());
            existing.setTauxCouvertureExperts(expertsResult.tauxCouverture());
            existing.setExpertsDetail(expertsResult.expertsDetailJson());

            String alignement = competencesResult.taux() >= 0.8 ? "Oui — aligné"
                    : competencesResult.taux() >= 0.5 ? "Partiel" : "Non aligné";
            existing.setAlignementStrategique(alignement);

            // 6. Compatibilité
            var config = scoringConfigService.getCurrent();
            if (config != null) {
                boolean compatible = competencesResult.taux() >= config.getSeuilCompatCompetences()
                        && expertsResult.tauxCouverture() >= config.getSeuilCompatExperts()
                        && !"Non aligné".equals(alignement);
                existing.setCompatibleMethodologie(compatible);
            }

            MatchingResult saved = matchingRepo.save(existing);
            
            // Recalcul en cascade du Scoring P-Win
            var analyse = analyseDossierRepository.findByDossierId(id).orElse(null);
            scoringEngine.calculate(dossier, analyse, saved);
            
            log.info("[Matching] Recalcul terminé — compétences={}% experts={}%",
                    Math.round(competencesResult.taux() * 100), Math.round(expertsResult.tauxCouverture() * 100));
            return ResponseEntity.ok(saved);

        } catch (Exception e) {
            log.error("[Matching] Erreur recalcul sans IA : {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── GET /api/matching/{id}/result ─────────────────────────────────────────

    /**
     * Retourne le résultat complet du matching.
     * Utilisé par la page matching Angular (onglets compétences, références, experts).
     */
    @GetMapping("/{id}/result")
    public ResponseEntity<MatchingResult> getResult(@PathVariable UUID id) {
        return ResponseEntity.ok(
                matchingRepo.findByDossierId(id)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Matching non disponible pour le dossier : " + id +
                                        " — lancer POST /api/matching/" + id + "/run d'abord")));
    }

    // ── GET /api/matching/{id}/matrix ─────────────────────────────────────────

    /**
     * Retourne la matrice de différenciation désérialisée.
     * Utilisée par la page matrice Angular pour affichage tabulaire.
     *
     * Réponse : liste de lignes [{critere, positionEgis, argumentGap}]
     * positionEgis ∈ { COUVERT, PARTIELLEMENT, NON_COUVERT, VIA_PARTENAIRE }
     */
    @GetMapping("/{id}/matrix")
    public ResponseEntity<Map<String, Object>> getMatrix(@PathVariable UUID id) {
        MatchingResult matching = matchingRepo.findByDossierId(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Matching non disponible pour le dossier : " + id));

        String matriceJson = matching.getMatriceDiff();

        return ResponseEntity.ok(Map.of(
                "dossierId",                 id,
                "matriceDiff",               matriceJson != null ? matriceJson : "[]",
                "tauxCouvertureCompetences", matching.getTauxCouvertureCompetences() != null
                        ? matching.getTauxCouvertureCompetences() : 0.0,
                "tauxCouvertureExperts",     matching.getTauxCouvertureExperts() != null
                        ? matching.getTauxCouvertureExperts() : 0.0,
                "relationClientNiveau",      matching.getRelationClientNiveau() != null
                        ? matching.getRelationClientNiveau() : 1,
                "alignementStrategique",     matching.getAlignementStrategique() != null
                        ? matching.getAlignementStrategique() : "Non évalué",
                "secteurAo",                 matching.getSecteurAo() != null
                        ? matching.getSecteurAo() : "",
                "gapRefs",                   matching.getGapRefs() != null
                        ? matching.getGapRefs() : "[]",
                "gapQualifs",                matching.getGapQualifs() != null
                        ? matching.getGapQualifs() : "[]"
        ));
    }

    /**
     * Enregistre les corrections humaines de la matrice sans relancer l'IA.
     * Utilisé après l'action explicite « Révalider la matrice ».
     */
    @PutMapping("/{id}/matrix/update")
    public ResponseEntity<Map<String, Object>> updateMatrix(
            @PathVariable UUID id,
            @RequestBody List<Map<String, Object>> matrix) {
        MatchingResult matching = matchingRepo.findByDossierId(id)
                .orElseThrow(() -> new IllegalArgumentException("Matching non disponible pour le dossier : " + id));
        try {
            matching.setMatriceDiff(objectMapper.writeValueAsString(matrix != null ? matrix : List.of()));
            matchingRepo.save(matching);
            log.info("[Matching] Matrice corrigée manuellement — dossier {}, {} ligne(s)", id,
                    matrix != null ? matrix.size() : 0);
            return ResponseEntity.ok(Map.of("status", "SAVED", "rows", matrix != null ? matrix.size() : 0));
        } catch (Exception e) {
            throw new IllegalStateException("Impossible d'enregistrer la matrice de différenciation", e);
        }
    }

    // ── POST /api/matching/{id}/force-compatible ──────────────────────────────

    /**
     * Force le passage vers la méthodologie malgré une incompatibilité détectée
     * automatiquement (couverture compétences/experts insuffisante, ou
     * alignement stratégique non aligné).
     *
     * Règles :
     *  - Justification obligatoire (min 50 mots)
     *  - Enregistré dans MatchingResult avec horodatage (traçabilité audit)
     *  - Une fois forcé, compatibleMethodologie passe à true définitivement
     *    pour ce dossier (le bouton "Générer méthodologie" devient actif)
     *  - Publie un événement RabbitMQ vers project-service pour traçabilité
     *    dans l'audit trail global (AuditTrailService côté project-service)
     *
     * Body JSON :
     * {
     *   "justification": "Le gap experts est temporaire, recrutement en cours...",
     *   "forcedBy": "Responsable Offre Maroc"
     * }
     */
    @PostMapping("/{id}/force-compatible")
    public ResponseEntity<Map<String, Object>> forceCompatible(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {

        String justification = body.get("justification");
        String forcedBy      = body.get("forcedBy");

        if (justification == null || justification.trim().split("\\s+").length < 50) {
            throw new IllegalArgumentException(
                    "La justification du forçage doit contenir au moins 50 mots. " +
                            "Reçu : " + (justification != null ? justification.trim().split("\\s+").length : 0) + " mots");
        }

        MatchingResult matching = matchingRepo.findByDossierId(id)
                .orElseThrow(() -> new IllegalStateException(
                        "Aucun matching trouvé pour ce dossier — lancer POST /api/matching/" + id + "/run d'abord"));

        if (Boolean.TRUE.equals(matching.getCompatibleMethodologie())) {
            throw new IllegalStateException(
                    "Ce dossier est déjà compatible — le forçage est inutile");
        }

        String motifOriginal = matching.getMotifIncompatibilite();

        matching.setCompatibleMethodologie(true);
        matching.setMotifIncompatibilite(
                "FORCÉ MANUELLEMENT — Motif original : " + motifOriginal
                        + " — Justification : " + justification
                        + " — Par : " + (forcedBy != null ? forcedBy : "Non renseigné"));

        matchingRepo.save(matching);

        // Publier pour traçabilité audit côté project-service
        analysteEventPublisher.publishForceCompatible(id, forcedBy, justification, motifOriginal);

        log.warn("[Matching] FORCE_COMPATIBLE — dossier {} par {}", id, forcedBy);

        return ResponseEntity.ok(Map.of(
                "status",        "FORCE_COMPATIBLE_ENREGISTRÉ",
                "dossierId",     id.toString(),
                "motifOriginal", motifOriginal != null ? motifOriginal : "",
                "message",       "Forçage enregistré. Le bouton de génération méthodologie est maintenant actif.",
                "avertissement", "Ce forçage est tracé et visible dans le rapport d'audit final."
        ));
    }
}
