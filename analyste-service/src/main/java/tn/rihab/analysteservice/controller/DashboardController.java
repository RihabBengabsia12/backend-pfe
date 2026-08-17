package tn.rihab.analysteservice.controller;

import tn.rihab.analysteservice.client.ProjectServiceClient;
import tn.rihab.analysteservice.dto.DossierDto;
import tn.rihab.analysteservice.model.*;
import tn.rihab.analysteservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Endpoint agrégé pour le Dashboard Analyste.
 * Fournit toutes les statistiques KPI en un seul appel.
 *
 * GET /api/analyses/dashboard-stats → Retourne tous les KPIs calculés
 */
@RestController
@RequestMapping("/api/analyses")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final PwinScoreRepository        pwinRepo;
    private final MatchingResultRepository   matchingRepo;
    private final AnalyseDossierRepository   analyseRepo;
    private final NoGoReportRepository       noGoReportRepo;
    private final IaAuditLogRepository       auditRepo;
    private final ProjectServiceClient       projectClient;

    @GetMapping("/dashboard-stats")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        log.info("[Dashboard] Calcul des KPIs agrégés");

        Map<String, Object> stats = new LinkedHashMap<>();

        // ── 1. Récupérer toutes les données ──────────────────────────────────
        List<PwinScore>      allScores   = pwinRepo.findAll();
        List<MatchingResult> allMatching = matchingRepo.findAll();
        List<AnalyseDossier> allAnalyses = analyseRepo.findAll();

        // ── 2. KPIs Pipeline ─────────────────────────────────────────────────
        stats.put("totalDossiers", allScores.size());
        stats.put("totalAnalyses", allAnalyses.size());

        // Distribution des décisions
        Map<String, Long> decisions = allScores.stream()
                .filter(s -> s.getDecisionAuto() != null)
                .collect(Collectors.groupingBy(PwinScore::getDecisionAuto, Collectors.counting()));
        stats.put("decisions", decisions);

        long goCount       = decisions.getOrDefault("GO", 0L) + decisions.getOrDefault("GO_CONDITIONNEL", 0L);
        long noGoCount     = decisions.getOrDefault("NO_GO", 0L);
        long manualCount   = decisions.getOrDefault("MANUAL", 0L);
        stats.put("goCount", goCount);
        stats.put("noGoCount", noGoCount);
        stats.put("manualCount", manualCount);

        // Taux de succès
        double tauxReussite = allScores.isEmpty() ? 0 :
                (double) goCount / allScores.size() * 100;
        stats.put("tauxReussite", Math.round(tauxReussite * 10.0) / 10.0);

        // ── 3. KPIs Scoring P-Win ────────────────────────────────────────────
        double avgPwin = allScores.stream()
                .filter(s -> s.getScoreGlobal() != null)
                .mapToDouble(PwinScore::getScoreGlobal)
                .average().orElse(0);
        stats.put("avgPwinScore", Math.round(avgPwin * 10.0) / 10.0);

        // Scores moyens par axe (pour Radar Chart)
        stats.put("avgScoreA", avgSafe(allScores, PwinScore::getScoreA));
        stats.put("avgScoreB", avgSafe(allScores, PwinScore::getScoreB));
        stats.put("avgScoreC", avgSafe(allScores, PwinScore::getScoreC));
        stats.put("avgScoreD", avgSafe(allScores, PwinScore::getScoreD));
        stats.put("avgScoreE", avgSafe(allScores, PwinScore::getScoreE));

        // Force-Go count
        long forceGoCount = allScores.stream()
                .filter(s -> Boolean.TRUE.equals(s.getForceGo())).count();
        stats.put("forceGoCount", forceGoCount);

        // Risques rédhibitoires
        long redhibitoireCount = allScores.stream()
                .filter(s -> Boolean.TRUE.equals(s.getRisqueRedhibitoire())).count();
        stats.put("redhibitoireCount", redhibitoireCount);

        // ── 4. KPIs Matching ─────────────────────────────────────────────────
        double avgCompetences = allMatching.stream()
                .filter(m -> m.getTauxCouvertureCompetences() != null)
                .mapToDouble(MatchingResult::getTauxCouvertureCompetences)
                .average().orElse(0);
        stats.put("avgCompetences", Math.round(avgCompetences * 1000.0) / 1000.0);

        double avgExperts = allMatching.stream()
                .filter(m -> m.getTauxCouvertureExperts() != null)
                .mapToDouble(MatchingResult::getTauxCouvertureExperts)
                .average().orElse(0);
        stats.put("avgExperts", Math.round(avgExperts * 1000.0) / 1000.0);

        double avgRelationClient = allMatching.stream()
                .filter(m -> m.getRelationClientNiveau() != null)
                .mapToInt(MatchingResult::getRelationClientNiveau)
                .average().orElse(0);
        stats.put("avgRelationClient", Math.round(avgRelationClient * 10.0) / 10.0);

        long compatibleCount = allMatching.stream()
                .filter(m -> Boolean.TRUE.equals(m.getCompatibleMethodologie())).count();
        stats.put("compatibleCount", compatibleCount);
        stats.put("totalMatching", allMatching.size());

        // ── 5. KPIs Risques ──────────────────────────────────────────────────
        Map<String, Map<String, Long>> riskDistribution = buildRiskDistribution(allAnalyses);
        stats.put("riskDistribution", riskDistribution);

        // Top risques les plus élevés/rédhibitoires
        Map<String, Long> topRisks = buildTopRisks(allAnalyses);
        stats.put("topRisks", topRisks);

        // ── 6. KPIs Documents & Audit (IA) ───────────────────────────────────
        long noGoReportsCount = noGoReportRepo.count();
        stats.put("noGoReportsCount", noGoReportsCount);

        List<IaAuditLog> allAudits = auditRepo.findAll();
        long totalProcessingTimeMs = allAudits.stream()
                .filter(a -> a.getProcessingTimeMs() != null)
                .mapToLong(IaAuditLog::getProcessingTimeMs)
                .sum();
        double totalCost = allAudits.stream()
                .filter(a -> a.getEstimatedCost() != null)
                .mapToDouble(IaAuditLog::getEstimatedCost)
                .sum();
        
        stats.put("totalProcessingTimeMs", totalProcessingTimeMs);
        stats.put("totalApiCost", totalCost);

        // ── 7. Distribution P-Win par tranches (pour histogramme) ────────────
        Map<String, Long> pwinBuckets = new LinkedHashMap<>();
        pwinBuckets.put("0-20",   allScores.stream().filter(s -> s.getScoreGlobal() != null && s.getScoreGlobal() < 20).count());
        pwinBuckets.put("20-40",  allScores.stream().filter(s -> s.getScoreGlobal() != null && s.getScoreGlobal() >= 20 && s.getScoreGlobal() < 40).count());
        pwinBuckets.put("40-60",  allScores.stream().filter(s -> s.getScoreGlobal() != null && s.getScoreGlobal() >= 40 && s.getScoreGlobal() < 60).count());
        pwinBuckets.put("60-80",  allScores.stream().filter(s -> s.getScoreGlobal() != null && s.getScoreGlobal() >= 60 && s.getScoreGlobal() < 80).count());
        pwinBuckets.put("80-100", allScores.stream().filter(s -> s.getScoreGlobal() != null && s.getScoreGlobal() >= 80).count());
        stats.put("pwinDistribution", pwinBuckets);

        // ── 8. Liste des derniers scores pour la table ───────────────────────
        List<Map<String, Object>> recentScores = allScores.stream()
                .sorted(Comparator.comparing(PwinScore::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .map(s -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("dossierId", s.getDossierId());
                    row.put("scoreGlobal", s.getScoreGlobal());
                    row.put("decision", s.getDecisionAuto());
                    row.put("scoreA", s.getScoreA());
                    row.put("scoreB", s.getScoreB());
                    row.put("scoreC", s.getScoreC());
                    row.put("scoreD", s.getScoreD());
                    row.put("scoreE", s.getScoreE());
                    row.put("forceGo", s.getForceGo());
                    row.put("risqueRedhibitoire", s.getRisqueRedhibitoire());
                    row.put("motifNogo", s.getMotifNogo());
                    row.put("createdAt", s.getCreatedAt());
                    return row;
                })
                .collect(Collectors.toList());
        stats.put("recentScores", recentScores);

        log.info("[Dashboard] KPIs calculés — {} scores, {} matching, {} analyses",
                allScores.size(), allMatching.size(), allAnalyses.size());

        return ResponseEntity.ok(stats);
    }

    // ── Utilitaires ──────────────────────────────────────────────────────────

    private double avgSafe(List<PwinScore> scores, java.util.function.Function<PwinScore, Double> getter) {
        return Math.round(scores.stream()
                .map(getter)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average().orElse(0) * 1000.0) / 1000.0;
    }

    /**
     * Construit la distribution des niveaux de risque par champ.
     */
    private Map<String, Map<String, Long>> buildRiskDistribution(List<AnalyseDossier> analyses) {
        String[] riskFields = {
                "RISQUE_PAYS_SECURITE", "RISQUES_FINANCIERS", "PENALITES",
                "EXIGENCES_TDR_INACCEPTABLES", "GARANTIES_ASSURANCES_ELEVEES",
                "TAILLE_DISPERSION", "FRAIS_DIVERS_ELEVES", "BUDGET_FAIBLE_HM_LIMITES",
                "PARTICIPATION_LOCALE_EXCESSIVE", "FISCALITE_NON_MAITRISEE"
        };

        Map<String, Map<String, Long>> result = new LinkedHashMap<>();
        for (String field : riskFields) {
            Map<String, Long> levels = new LinkedHashMap<>();
            levels.put("Faible", 0L);
            levels.put("Modéré", 0L);
            levels.put("Élevé", 0L);
            levels.put("Rédhibitoire", 0L);

            for (AnalyseDossier a : analyses) {
                String raw = getRiskValue(a, field);
                if (raw != null) {
                    String niveau = raw.contains("||") ? raw.substring(0, raw.indexOf("||")).trim() : raw.trim();
                    // Normalize the level
                    String normalized = normalizeLevel(niveau);
                    levels.merge(normalized, 1L, Long::sum);
                }
            }
            result.put(field, levels);
        }
        return result;
    }

    /**
     * Construit le top des risques élevés/rédhibitoires.
     */
    private Map<String, Long> buildTopRisks(List<AnalyseDossier> analyses) {
        String[] riskFields = {
                "RISQUE_PAYS_SECURITE", "RISQUES_FINANCIERS", "PENALITES",
                "EXIGENCES_TDR_INACCEPTABLES", "GARANTIES_ASSURANCES_ELEVEES",
                "TAILLE_DISPERSION", "FRAIS_DIVERS_ELEVES", "BUDGET_FAIBLE_HM_LIMITES",
                "PARTICIPATION_LOCALE_EXCESSIVE", "FISCALITE_NON_MAITRISEE"
        };

        Map<String, Long> counts = new LinkedHashMap<>();
        for (String field : riskFields) {
            long highCount = analyses.stream()
                    .map(a -> getRiskValue(a, field))
                    .filter(Objects::nonNull)
                    .filter(raw -> {
                        String niveau = raw.contains("||") ? raw.substring(0, raw.indexOf("||")).trim() : raw.trim();
                        String norm = normalizeLevel(niveau);
                        return "Élevé".equals(norm) || "Rédhibitoire".equals(norm);
                    })
                    .count();
            if (highCount > 0) counts.put(field, highCount);
        }

        // Trier par count décroissant
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    private String getRiskValue(AnalyseDossier a, String field) {
        return switch (field) {
            case "RISQUE_PAYS_SECURITE"         -> a.getRisquePaysSecurite();
            case "RISQUES_FINANCIERS"           -> a.getRisquesFinanciers();
            case "PENALITES"                    -> a.getPenalites();
            case "EXIGENCES_TDR_INACCEPTABLES"  -> a.getExigencesTdrInacceptables();
            case "GARANTIES_ASSURANCES_ELEVEES" -> a.getGarantiesAssurancesElevees();
            case "TAILLE_DISPERSION"            -> a.getTailleDispersion();
            case "FRAIS_DIVERS_ELEVES"          -> a.getFraisDiversEleves();
            case "BUDGET_FAIBLE_HM_LIMITES"     -> a.getBudgetFaibleHmLimites();
            case "PARTICIPATION_LOCALE_EXCESSIVE"-> a.getParticipationLocaleExcessive();
            case "FISCALITE_NON_MAITRISEE"      -> a.getFiscaliteNonMaitrisee();
            default -> null;
        };
    }

    private String normalizeLevel(String niveau) {
        if (niveau == null) return "Faible";
        String n = niveau.toUpperCase()
                .replaceAll("[ÉÈÊËéèêë]", "E")
                .replaceAll("[ÀÁÂÃàáâã]", "A")
                .replaceAll("[ÔÖôö]", "O");
        if (n.contains("REDHIBITOIRE")) return "Rédhibitoire";
        if (n.contains("ELEVE"))        return "Élevé";
        if (n.contains("MODERE"))       return "Modéré";
        return "Faible";
    }
}
