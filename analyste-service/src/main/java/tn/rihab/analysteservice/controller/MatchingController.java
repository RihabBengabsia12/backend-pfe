package tn.rihab.analysteservice.controller;

import tn.rihab.analysteservice.client.ProjectServiceClient;
import tn.rihab.analysteservice.matching.MatchingEngine;
import tn.rihab.analysteservice.messaging.AnalysteEventPublisher;
import tn.rihab.analysteservice.model.MatchingResult;
import tn.rihab.analysteservice.repository.MatchingResultRepository;
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