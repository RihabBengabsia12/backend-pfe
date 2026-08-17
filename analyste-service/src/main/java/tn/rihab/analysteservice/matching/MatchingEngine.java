package tn.rihab.analysteservice.matching;

import com.fasterxml.jackson.databind.ObjectMapper;
import tn.rihab.analysteservice.dto.DossierDto;
import tn.rihab.analysteservice.client.IaServiceClient;
import tn.rihab.analysteservice.dto.ia.*;
import tn.rihab.analysteservice.matching.matchers.*;
import tn.rihab.analysteservice.model.MatchingResult;
import tn.rihab.analysteservice.model.ScoringConfig;
import tn.rihab.analysteservice.repository.CompetenceRepository;
import tn.rihab.analysteservice.repository.ReferenceRepository;
import tn.rihab.analysteservice.repository.ExpertProfilRepository;
import tn.rihab.analysteservice.repository.MatchingResultRepository;
import tn.rihab.analysteservice.repository.IaAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.rihab.analysteservice.scoring.ScoringConfigService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Coordonne l'analyse multidimensionnelle du matching Phase 3.
 * Version optimisée avec Jackson pour une sérialisation robuste.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MatchingEngine {

    private final IaServiceClient         iaClient;
    private final CompetencesMatcher      competencesMatcher;
    private final ReferencesMatcher       referencesMatcher;
    private final ExpertsMatcher          expertsMatcher;
    private final ClientMatcher           clientMatcher;
    private final MatchingResultRepository matchingRepo;
    private final ObjectMapper            objectMapper; // 🚀 Injecté automatiquement par Spring
    private final ScoringConfigService scoringConfigService;
    
    private final CompetenceRepository competenceRepository;
    private final ReferenceRepository referenceRepository;
    private final ExpertProfilRepository expertProfilRepository;
    private final IaAuditLogRepository auditLogRepo;

    /**
     * Lance le matching complet pour un dossier.
     */
    @Transactional
    public MatchingResult runMatching(DossierDto dossier, String documentText) {
        UUID dossierId = dossier.getId();
        log.info("[Matching] Démarrage Phase 3 pour dossier {}", dossierId);

        // ── Étape 1 : Extraction des exigences depuis la DP ───────────────────
        java.util.Map<String, Object> referentiel = new java.util.HashMap<>();
        referentiel.put("competences", competenceRepository.findByActifTrue().stream().map(c -> c.getDomaine() + (c.getSousDomaine() != null ? " - " + c.getSousDomaine() : "")).distinct().toList());
        referentiel.put("references", referenceRepository.findByActifTrue().stream().map(r -> r.getTitre()).toList());
        referentiel.put("experts", expertProfilRepository.findByActifTrue().stream().map(e -> e.getNom() + " (" + String.join(", ", e.getSpecialites()) + ")").toList());

        ExtractionRequestDto reqExigences = ExtractionRequestDto.builder()
                .dossierId(dossierId)
                .documentText(documentText)
                .phase("REQUIREMENTS")
                .referentiel(referentiel)
                .build();
        RequirementsResponseDto requirements = iaClient.extractRequirements(reqExigences);

        // Sauvegarder l'audit pour l'extraction des exigences (Phase 3)
        if (requirements.getToken_usage() != null) {
            String rawJsonStr = "{}";
            try {
                rawJsonStr = objectMapper.writeValueAsString(requirements);
            } catch (Exception e) {
                log.warn("Impossible de sérialiser la réponse Phase 3 (Requirements) en JSON", e);
            }
            auditLogRepo.save(tn.rihab.analysteservice.model.IaAuditLog.builder()
                    .dossierId(dossierId)
                    .actionName("EXTRACTION_PHASE_3")
                    .tokenUsage(requirements.getToken_usage())
                    .inputTokens(requirements.getInput_tokens())
                    .outputTokens(requirements.getOutput_tokens())
                    .processingTimeMs(requirements.getProcessing_time_ms())
                    .estimatedCost(requirements.getEstimated_cost())
                    .cacheCreationTokens(requirements.getCache_creation_tokens())
                    .cacheReadTokens(requirements.getCache_read_tokens())
                    .rawJson(rawJsonStr)
                    .build());
        }

        // ── Étape 2 : Matching compétences ────────────────────────────────────
        var competencesResult = competencesMatcher.match(requirements.getQualifsExigees());

        // ── Étape 3 : Matching références ─────────────────────────────────────
        Double budget = extractBudget(dossier.getBudgetGlobal());
        var referencesResult = referencesMatcher.match(
                requirements.getSecteurDetecte(), dossier.getPays(), budget);

        // ── Étape 4 : Matching experts ────────────────────────────────────────
        var expertsResult = expertsMatcher.match(requirements.getExpertsRequis(), dossier);

        // ── Étape 5 : Relation client ─────────────────────────────────────────
        var clientResult = clientMatcher.match(dossier.getClient());

        // ── Étape 6 : Matrice de différenciation via ia-service ───────────────
        MatchingContextDto context = MatchingContextDto.builder()
                .dossierId(dossierId)
                .refsExigees(requirements.getRefsExigees())
                .qualifsExigees(requirements.getQualifsExigees())
                .expertsRequis(requirements.getExpertsRequis())
                .tauxCouvertureCompetences(competencesResult.taux())
                .tauxCouvertureExperts(expertsResult.tauxCouverture())
                .gapRefs(referencesResult.gapRefsJson())
                .pays(dossier.getPays())
                .secteur(requirements.getSecteurDetecte())
                .bailleurs(dossier.getBailleurs())
                .build();

        MatrixResponseDto matrice = iaClient.generateMatrix(context);

        // Sauvegarder l'audit pour la matrice
        if (matrice != null && matrice.getToken_usage() != null) {
            String rawJsonStr = "{}";
            try {
                rawJsonStr = objectMapper.writeValueAsString(matrice);
            } catch (Exception e) {
                log.warn("Impossible de sérialiser la réponse Phase 3 (Matrice) en JSON", e);
            }
            auditLogRepo.save(tn.rihab.analysteservice.model.IaAuditLog.builder()
                    .dossierId(dossierId)
                    .actionName("GENERATION_MATRICE")
                    .tokenUsage(matrice.getToken_usage())
                    .inputTokens(matrice.getInput_tokens())
                    .outputTokens(matrice.getOutput_tokens())
                    .processingTimeMs(matrice.getProcessing_time_ms())
                    .estimatedCost(matrice.getEstimated_cost())
                    .cacheCreationTokens(matrice.getCache_creation_tokens())
                    .cacheReadTokens(matrice.getCache_read_tokens())
                    .rawJson(rawJsonStr)
                    .build());
        }

        // Sérialisation sécurisée via Jackson
        String matriceJson = serializeObject(matrice != null ? matrice.getLignes() : null);

        // Alignement stratégique
        String alignement = competencesResult.taux() >= 0.8 ? "Oui — aligné"
                : competencesResult.taux() >= 0.5 ? "Partiel" : "Non aligné";

        // ── Étape 7 : Persister MatchingResult ───────────────────────────────
        MatchingResult result = matchingRepo.findByDossierId(dossierId)
                .orElse(MatchingResult.builder().dossierId(dossierId).build());

        result.setTauxCouvertureCompetences(competencesResult.taux());
        result.setCompetencesDetail(competencesResult.detailJson());
        result.setRefsExigees(serializeObject(requirements.getRefsExigees()));
        result.setGapRefs(referencesResult.gapRefsJson());
        result.setExpertsRequis(serializeObject(requirements.getExpertsRequis()));
        result.setTauxCouvertureExperts(expertsResult.tauxCouverture());
        result.setExpertsDetail(expertsResult.expertsDetailJson());
        result.setRelationClientNiveau(clientResult.niveau());
        result.setRelationClientNbMissions(clientResult.nbMissions());
        result.setMatriceDiff(matriceJson);
        result.setQualifsExigees(serializeObject(requirements.getQualifsExigees()));
        result.setSecteurAo(requirements.getSecteurDetecte());
        result.setAlignementStrategique(alignement);
        result.setTypeContrat(requirements.getTypeContrat());

        // Calcul de la décision de compatibilité
        ScoringConfig config = scoringConfigService.getCurrent();
        boolean compatible = isCompatible(result, config);
        result.setCompatibleMethodologie(compatible);
        result.setMotifIncompatibilite(compatible ? null : buildMotifIncompatibilite(result, config));

        MatchingResult saved = matchingRepo.save(result);

        log.info("[Matching] Dossier {} — compétences={:.0f}% experts={:.0f}% client=niv{} alignement={}",
                dossierId, competencesResult.taux() * 100, expertsResult.tauxCouverture() * 100,
                clientResult.niveau(), alignement);

        return saved;
    }

    // ── Utilitaires de sérialisation Jackson ───────────────────────────────────

    /**
     * Convertit n'importe quel objet ou liste en JSON String de manière sécurisée.
     */
    private String serializeObject(Object obj) {
        if (obj == null) return "[]";
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.error("[MatchingEngine] Erreur lors de la sérialisation en JSON", e);
            return "[]";
        }
    }

    /**
     * Extrait le budget en interprétant les modificateurs textuels (M, M€, K, Millions)
     */
    private Double extractBudget(String budgetGlobal) {
        if (budgetGlobal == null || budgetGlobal.isBlank()) return null;
        try {
            String clean = budgetGlobal.toLowerCase().trim();

            double multiplicateur = 1.0;
            if (clean.contains("m") || clean.contains("million")) {
                multiplicateur = 1_000_000.0;
            } else if (clean.contains("k") || clean.contains("mille")) {
                multiplicateur = 1_000.0;
            }

            // Nettoyer en conservant uniquement les chiffres et les séparateurs décimaux
            String chiffres = clean.replaceAll("[^0-9.,]", "").replace(",", ".");
            if (chiffres.isBlank()) return null;

            return Double.parseDouble(chiffres) * multiplicateur;
        } catch (NumberFormatException e) {
            log.warn("[MatchingEngine] Impossible de parser le budget global : {}", budgetGlobal);
            return null;
        }
    }
    /**
     * Détermine si le dossier est compatible pour poursuivre vers la méthodologie.
     * Règle : les 3 conditions doivent être vraies simultanément.
     */
    private boolean isCompatible(MatchingResult matching, ScoringConfig config) {
        boolean competencesOk = matching.getTauxCouvertureCompetences() != null
                && matching.getTauxCouvertureCompetences() >= config.getSeuilCompatCompetences();

        boolean expertsOk = matching.getTauxCouvertureExperts() != null
                && matching.getTauxCouvertureExperts() >= config.getSeuilCompatExperts();

        boolean alignementOk = matching.getAlignementStrategique() != null
                && !"Non".equalsIgnoreCase(matching.getAlignementStrategique());

        return competencesOk && expertsOk && alignementOk;
    }

    /** Construit le texte explicatif des motifs d'incompatibilité. */
    private String buildMotifIncompatibilite(MatchingResult m, ScoringConfig config) {
        List<String> motifs = new ArrayList<>();

        if (m.getTauxCouvertureCompetences() == null
                || m.getTauxCouvertureCompetences() < config.getSeuilCompatCompetences()) {
            motifs.add(String.format("Couverture compétences insuffisante (%.0f%% < %.0f%%)",
                    m.getTauxCouvertureCompetences() != null ? m.getTauxCouvertureCompetences() * 100 : 0,
                    config.getSeuilCompatCompetences() * 100));
        }
        if (m.getTauxCouvertureExperts() == null
                || m.getTauxCouvertureExperts() < config.getSeuilCompatExperts()) {
            motifs.add(String.format("Couverture experts insuffisante (%.0f%% < %.0f%%)",
                    m.getTauxCouvertureExperts() != null ? m.getTauxCouvertureExperts() * 100 : 0,
                    config.getSeuilCompatExperts() * 100));
        }
        if ("Non".equalsIgnoreCase(m.getAlignementStrategique())) {
            motifs.add("Alignement stratégique non aligné");
        }

        return String.join(" · ", motifs);
    }
}