package tn.rihab.analysteservice.controller;

import tn.rihab.analysteservice.client.ProjectServiceClient;
import tn.rihab.analysteservice.model.AnalyseDossier;
import tn.rihab.analysteservice.service.AnalyseDeepService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.http.HttpStatus;
import java.util.Map;
import java.util.List;
import java.util.UUID;

/**
 * Endpoints Phase 2 — Analyse approfondie.
 * Base URL : /api/analyses
 *
 * POST /api/analyses/{id}/deep-analysis   Déclenche extraction P2 + risques
 * GET  /api/analyses/{id}/extraction-p2   Retourne les 16 champs extraits
 * PUT  /api/analyses/{id}/validate-p2     Valide/corrige les champs P2
 * GET  /api/analyses/{id}/risks           Retourne les 10 risques évalués
 * PUT  /api/analyses/{id}/risks/validate  Valide/corrige les niveaux de risques
 */
@RestController
@RequestMapping("/api/analyses")
@RequiredArgsConstructor
@Slf4j
public class AnalyseController {

    private final AnalyseDeepService    analyseDeepService;
    private final ProjectServiceClient  projectClient;

    // ── POST /api/analyses/{id}/deep-analysis ─────────────────────────────────

    /**
     * Déclenche manuellement l'extraction Phase 2 pour un dossier.
     * En fonctionnement normal, cette phase est déclenchée automatiquement
     * par RabbitMQ (DOSSIER_INDEXED). Cet endpoint permet un re-déclenchement
     * manuel depuis Angular (ex: après correction Phase 1).
     *
     * Lance en asynchrone : extraction 16 champs + 10 risques.
     * Réponse immédiate 202 Accepted — Angular polling sur /status.
     */
    @PostMapping("/{id}/deep-analysis")
    public ResponseEntity<Map<String, String>> triggerDeepAnalysis(
            @PathVariable UUID id) {

        log.info("[AnalyseController] Déclenchement manuel Phase 2 — dossier {}", id);

        // Vérifier que le dossier est bien INDEXED
        var dossier = projectClient.getDossier(id);
        if (!"INDEXED".equals(dossier.getStatus())
                && !"DEEP_ANALYSIS".equals(dossier.getStatus())
                && !"CORRECTION_LOOP".equals(dossier.getStatus())) {
            throw new IllegalStateException(
                    "Phase 2 ne peut être déclenchée que depuis le statut INDEXED. " +
                            "Statut actuel : " + dossier.getStatus());
        }

        // Lancer en asynchrone (non bloquant)
        analyseDeepService.runFullPipeline(id);

        return ResponseEntity.accepted().body(Map.of(
                "status",  "STARTED",
                "message", "Phase 2 déclenchée — extraction en cours",
                "dossierId", id.toString()
        ));
    }

    @PostMapping("/batch-deep-analysis")
    public ResponseEntity<Map<String, String>> triggerBatchDeepAnalysis(@RequestBody List<UUID> ids) {
        log.info("[AnalyseController] Déclenchement manuel Phase 2 par lot — {} dossiers", ids.size());

        for(UUID id : ids) {
            var dossier = projectClient.getDossier(id);
            if (!"INDEXED".equals(dossier.getStatus())
                    && !"DEEP_ANALYSIS".equals(dossier.getStatus())
                    && !"CORRECTION_LOOP".equals(dossier.getStatus())) {
                log.warn("Dossier {} ignoré pour batch Phase 2 (statut invalide: {})", id, dossier.getStatus());
                continue;
            }
            analyseDeepService.runFullPipeline(id);
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "message", "Phase 2 déclenchée par lot avec succès pour " + ids.size() + " dossiers",
                "status", "DEEP_ANALYSIS"
        ));
    }

    // ── POST /api/analyses/{id}/generate-deliverables ─────────────────────────

    /**
     * Déclenche manuellement la génération des livrables finaux (Phase 4).
     * Doit être appelé par l'analyste une fois les validations terminées.
     */
    @PostMapping("/{id}/generate-deliverables")
    public ResponseEntity<Map<String, String>> generateDeliverables(@PathVariable UUID id) {
        log.info("[AnalyseController] Déclenchement manuel de la génération des livrables — dossier {}", id);

        var dossier = projectClient.getDossier(id);
        if (!"INDEXED".equals(dossier.getStatus())
                && !"DEEP_ANALYSIS".equals(dossier.getStatus())
                && !"CORRECTION_LOOP".equals(dossier.getStatus())) {
            throw new IllegalStateException(
                    "Génération impossible. Statut actuel : " + dossier.getStatus());
        }

        // Lancer la génération en asynchrone
        analyseDeepService.generateDeliverables(id);

        return ResponseEntity.accepted().body(Map.of(
                "status",  "STARTED",
                "message", "Génération des livrables en cours",
                "dossierId", id.toString()
        ));
    }

    // ── GET /api/analyses/{id}/extraction-p2 ──────────────────────────────────

    /**
     * Retourne les 16 champs Phase 2 extraits par Claude.
     * Utilisé par Angular pour afficher l'interface de validation Phase 2.
     *
     * Champs retournés : NOTE_MINIMALE, PON_TECH, PON_FIN,
     *   DELAI_GLOBAL_MOIS, DATE_LIMITE_QUESTIONS, FIN_LOCAL_OUI_NON,
     *   FINA_LOCAL_DETAILS, CAUTION_MONTANT, CAUTION_MONNAIE,
     *   CAUTION_DUREE, BANQUE_LOCALE_EXIGEE, DATE_LIMITE_SOUMISSION,
     *   DELAI_PREP_SUF (calculé), JUSTIF_DELAI_PREP (généré),
     *   CAPACITE_DELAI (saisie manuelle), TRANSMISSION (saisie manuelle)
     */
    @GetMapping("/{id}/extraction-p2")
    public ResponseEntity<AnalyseDossier> getExtractionP2(@PathVariable UUID id) {
        return ResponseEntity.ok(analyseDeepService.getAnalyse(id));
    }

    // ── PUT /api/analyses/{id}/validate-p2 ────────────────────────────────────

    /**
     * Valide et enregistre les corrections Phase 2 faites par l'analyste.
     * Champs manuels obligatoires dans ce body :
     *   - capaciteDelai : "Oui" / "Non"
     *   - justifCapaciteDelai : texte (obligatoire si "Non")
     *   - transmission : date JJ/MM/AAAA (optionnel)
     *
     * Les autres champs peuvent être corrigés si Claude s'est trompé.
     *
     * Body JSON :
     * {
     *   "noteMinimale": "70/100",
     *   "ponTech": 80.0,
     *   "ponFin": 20.0,
     *   "cautionMontant": "50000",
     *   "capaciteDelai": "Oui",
     *   "justifCapaciteDelai": "Chef de mission disponible dès janvier"
     * }
     */
    @PutMapping("/{id}/validate-p2")
    public ResponseEntity<AnalyseDossier> validateP2(
            @PathVariable UUID id,
            @RequestBody AnalyseDossier corrections) {

        // Validation des champs manuels obligatoires
        if (corrections.getCapaciteDelai() == null) {
            throw new IllegalArgumentException(
                    "[[CAPACITE_DELAI]] est obligatoire (saisie Responsable d'Offre)");
        }
        if ("Non".equalsIgnoreCase(corrections.getCapaciteDelai())
                && (corrections.getJustifCapaciteDelai() == null
                || corrections.getJustifCapaciteDelai().isBlank())) {
            throw new IllegalArgumentException(
                    "[[JUSTIF_CAPACITE_DELAI]] est obligatoire quand [[CAPACITE_DELAI]] = Non");
        }

        // Validation pondérations
        if (corrections.getPonTech() != null && corrections.getPonFin() != null) {
            double somme = corrections.getPonTech() + corrections.getPonFin();
            if (Math.abs(somme - 100.0) > 0.5) {
                throw new IllegalArgumentException(
                        "[[PON_TECH]] + [[PON_FIN]] doit être égal à 100 (reçu : " + somme + ")");
            }
        }

        log.info("[AnalyseController] Validation P2 — dossier {} (capaciteDelai={})",
                id, corrections.getCapaciteDelai());

        AnalyseDossier saved = analyseDeepService.updateAnalyse(id, corrections);
        analyseDeepService.recalculatePipeline(id);
        return ResponseEntity.ok(saved);
    }

    // ── GET /api/analyses/{id}/risks ──────────────────────────────────────────

    /**
     * Retourne les 10 risques évalués par Claude avec leur niveau et justification.
     * Format : AnalyseDossier avec uniquement les champs de risques.
     *
     * Chaque risque stocké au format "NIVEAU||justification".
     * Angular décompose pour afficher le sélecteur (Faible/Modéré/Élevé/Rédhibitoire)
     * et la justification éditable.
     */
    @GetMapping("/{id}/risks")
    public ResponseEntity<Map<String, Object>> getRisks(@PathVariable UUID id) {
        AnalyseDossier analyse = analyseDeepService.getAnalyse(id);

        // Retourner uniquement les champs de risques pour l'interface
        return ResponseEntity.ok(Map.of(
                "RISQUE_PAYS_SECURITE",         decompose(analyse.getRisquePaysSecurite()),
                "RISQUES_FINANCIERS",           decompose(analyse.getRisquesFinanciers()),
                "PENALITES",                    decompose(analyse.getPenalites()),
                "EXIGENCES_TDR_INACCEPTABLES",  decompose(analyse.getExigencesTdrInacceptables()),
                "GARANTIES_ASSURANCES_ELEVEES", decompose(analyse.getGarantiesAssurancesElevees()),
                "TAILLE_DISPERSION",            decompose(analyse.getTailleDispersion()),
                "FRAIS_DIVERS_ELEVES",          decompose(analyse.getFraisDiversEleves()),
                "BUDGET_FAIBLE_HM_LIMITES",     decompose(analyse.getBudgetFaibleHmLimites()),
                "PARTICIPATION_LOCALE_EXCESSIVE",decompose(analyse.getParticipationLocaleExcessive()),
                "FISCALITE_NON_MAITRISEE",      decompose(analyse.getFiscaliteNonMaitrisee())
        ));
    }

    // ── PUT /api/analyses/{id}/risks/validate ─────────────────────────────────

    /**
     * Valide et enregistre les corrections de niveaux de risques par l'analyste.
     * Claude propose un niveau initial — l'analyste peut le modifier.
     *
     * Body JSON :
     * {
     *   "PENALITES":              {"niveau": "Rédhibitoire", "justification": "Pénalités 15% non plafonnées"},
     *   "RISQUE_PAYS_SECURITE":   {"niveau": "Élevé",        "justification": "Zone orange MEAE"},
     *   "RISQUES_FINANCIERS":     {"niveau": "Modéré",       "justification": "Contrat en FCFA, risque change limité"},
     *   ...
     * }
     *
     * ⚠️ Si un risque = "Rédhibitoire" → le scoring forcera P-Win = 0
     * → Alerte affichée immédiatement côté Angular.
     */
    @PutMapping("/{id}/risks/validate")
    public ResponseEntity<AnalyseDossier> validateRisks(
            @PathVariable UUID id,
            @RequestBody Map<String, Map<String, String>> risques) {

        log.info("[AnalyseController] Validation risques — dossier {} ({} risques soumis)",
                id, risques.size());

        // Reconstruire un AnalyseDossier partiel avec uniquement les risques
        AnalyseDossier corrections = new AnalyseDossier();
        corrections.setDossierId(id);

        risques.forEach((champ, data) -> {
            String niveau      = data.getOrDefault("niveau", "Faible");
            String justif      = data.getOrDefault("justification", "");
            String valeurRisque = niveau + "||" + justif;

            switch (champ) {
                case "RISQUE_PAYS_SECURITE"          -> corrections.setRisquePaysSecurite(valeurRisque);
                case "RISQUES_FINANCIERS"            -> corrections.setRisquesFinanciers(valeurRisque);
                case "PENALITES"                     -> corrections.setPenalites(valeurRisque);
                case "EXIGENCES_TDR_INACCEPTABLES"   -> corrections.setExigencesTdrInacceptables(valeurRisque);
                case "GARANTIES_ASSURANCES_ELEVEES"  -> corrections.setGarantiesAssurancesElevees(valeurRisque);
                case "TAILLE_DISPERSION"             -> corrections.setTailleDispersion(valeurRisque);
                case "FRAIS_DIVERS_ELEVES"           -> corrections.setFraisDiversEleves(valeurRisque);
                case "BUDGET_FAIBLE_HM_LIMITES"      -> corrections.setBudgetFaibleHmLimites(valeurRisque);
                case "PARTICIPATION_LOCALE_EXCESSIVE" -> corrections.setParticipationLocaleExcessive(valeurRisque);
                case "FISCALITE_NON_MAITRISEE"       -> corrections.setFiscaliteNonMaitrisee(valeurRisque);
            }
        });

        AnalyseDossier saved = analyseDeepService.updateAnalyse(id, corrections);

        // Vérifier si un risque Rédhibitoire est présent → alerter Angular
        boolean hasRedhibitoire = risques.values().stream()
                .anyMatch(r -> "Rédhibitoire".equals(r.get("niveau")));

        if (hasRedhibitoire) {
            log.warn("[AnalyseController] Risque Rédhibitoire détecté — dossier {} — P-Win sera forcé à 0", id);
        }

        analyseDeepService.recalculatePipeline(id);

        return ResponseEntity.ok(saved);
    }

    // ── Utilitaire ─────────────────────────────────────────────────────────────

    /**
     * Décompose "NIVEAU||justification" en Map {niveau, justification}
     * pour faciliter la consommation côté Angular.
     */
    private Map<String, String> decompose(String valeur) {
        if (valeur == null) return Map.of("niveau", "", "justification", "");
        int idx = valeur.indexOf("||");
        if (idx < 0) return Map.of("niveau", valeur.trim(), "justification", "");
        return Map.of(
                "niveau",       valeur.substring(0, idx).trim(),
                "justification", valeur.substring(idx + 2).trim()
        );
    }
}