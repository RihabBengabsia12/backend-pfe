package tn.rihab.analysteservice.scoring;

import tn.rihab.analysteservice.dto.DossierDto;
import tn.rihab.analysteservice.model.*;
import tn.rihab.analysteservice.repository.PwinScoreRepository;
import tn.rihab.analysteservice.scoring.calculators.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Point d'entrée unique du calcul P-Win.
 * Version Senior : Découplage total via l'injection de la liste des calculateurs d'axes.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScoringEngine {

    // 🚀 Spring injecte dynamiquement tous les beans implémentant AxeCalculator
    private final List<AxeCalculator> calculators;
    private final AxeRisquesCalculator axeRisquesCalculator; // Conservé pour la structure RisquesResult dédiée
    private final ScoringConfigService scoringConfigService;
    private final PwinScoreRepository pwinRepo;

    /**
     * Calcule le score P-Win et le persiste de manière dynamique.
     */
    @Transactional
    public PwinScore calculate(DossierDto dossierDto, AnalyseDossier analyse, MatchingResult matching) {

        if (dossierDto == null) {
            log.error("[Scoring] Impossible de calculer le P-Win : DossierDto est nul.");
            return null;
        }

        UUID dossierId = dossierDto.getId();
        ScoringConfig config = scoringConfigService.getCurrent();

        if (config == null) {
            log.warn("[Scoring] Configuration introuvable, utilisation de valeurs de secours (poids égaux)");
            config = createDefaultConfig();
        }

        // 1. ── RÈGLE ABSOLUE : Analyse prioritaire des risques (Axe C) ──
        AxeRisquesCalculator.RisquesResult risquesResult = axeRisquesCalculator.calculate(analyse);

        // Variables pour stocker les scores individuels nécessaires à la persistance historique
        double sA = 0.0, sB = 0.0, sC = risquesResult.getScore(), sD = 0.0, sE = 0.0;
        double scoreGlobal = 0.0;

        if (risquesResult.isRedhibitoire()) {
            scoreGlobal = 0.0;
            // On calcule quand même les scores individuels pour l'affichage/historique en BDD
            sA = getScoreSafe("AXE_A", dossierDto, analyse, matching, config);
            sB = getScoreSafe("AXE_B", dossierDto, analyse, matching, config);
            sD = getScoreSafe("AXE_D", dossierDto, analyse, matching, config);
            sE = getScoreSafe("AXE_E", dossierDto, analyse, matching, config);
        } else {
            // 2. ── CALCUL DYNAMIQUE DU SCORE GLOBAL (Moyenne Pondérée) ──
            double sommePointsPonderes = 0.0;

            for (AxeCalculator calculator : calculators) {
                double scoreAxe = calculator.calculate(dossierDto, analyse, matching, config);
                double poidsAxe = calculator.getPoids(config);

                sommePointsPonderes += (scoreAxe * poidsAxe);

                // Assignation aux variables locales pour la sauvegarde explicite en colonne BDD
                switch (calculator.getAxeCode()) {
                    case "AXE_A" -> sA = scoreAxe;
                    case "AXE_B" -> sB = scoreAxe;
                    case "AXE_D" -> sD = scoreAxe;
                    case "AXE_E" -> sE = scoreAxe;
                }
            }
            scoreGlobal = Math.max(0.0, Math.min(100.0, sommePointsPonderes * 100.0));
        }

        // 3. ── DÉCISION AUTOMATIQUE ──
        String decision;
        if (risquesResult.isRedhibitoire() || scoreGlobal < config.getSeuilNoGo()) {
            decision = "NO_GO";
        } else if (scoreGlobal < config.getSeuilGoConditionnel()) {
            decision = "MANUAL";
        } else if (scoreGlobal < config.getSeuilGoFort()) {
            decision = "GO_CONDITIONNEL";
        } else {
            decision = "GO";
        }

        // 4. ── MOTIF NO-GO PRINCIPAL DYNAMIQUE ──
        String motifNogo = null;
        if ("NO_GO".equals(decision) || "MANUAL".equals(decision)) {
            if (risquesResult.isRedhibitoire()) {
                motifNogo = "Risque rédhibitoire détecté : [[" + risquesResult.getChampRedhibitoire() + "]]";
            } else {
                motifNogo = buildMotifPrincipal(dossierDto, analyse, matching, config);
            }
        }

        log.info("[Scoring] Dossier {} — P-Win={:.1f}% ({}) | A={:.2f} B={:.2f} C={:.2f} D={:.2f} E={:.2f}",
                dossierId, scoreGlobal, decision, sA, sB, sC, sD, sE);

        // 5. ── PERSISTANCE (UPSERT) ──
        PwinScore pwin = pwinRepo.findByDossierId(dossierId)
                .orElse(PwinScore.builder().dossierId(dossierId).build());

        pwin.setScoreGlobal(Math.round(scoreGlobal * 10.0) / 10.0);
        pwin.setScoreA(Math.round(sA * 1000.0) / 1000.0);
        pwin.setScoreB(Math.round(sB * 1000.0) / 1000.0);
        pwin.setScoreC(Math.round(sC * 1000.0) / 1000.0);
        pwin.setScoreD(Math.round(sD * 1000.0) / 1000.0);
        pwin.setScoreE(Math.round(sE * 1000.0) / 1000.0);
        pwin.setDecisionAuto(decision);
        pwin.setMotifNogo(motifNogo);
        pwin.setRisqueRedhibitoire(risquesResult.isRedhibitoire());
        pwin.setRisqueRedhibitoireChamp(risquesResult.getChampRedhibitoire());

        return pwinRepo.save(pwin);
    }

    /**
     * Détermine dynamiquement le motif d'échec basé sur l'axe le plus faible.
     */
    private String buildMotifPrincipal(DossierDto d, AnalyseDossier a, MatchingResult m, ScoringConfig c) {
        AxeCalculator pireAxe = null;
        double pireScore = 1.0;

        for (AxeCalculator calculator : calculators) {
            double score = calculator.calculate(d, a, m, c);
            if (score < pireScore) {
                pireScore = score;
                pireAxe = calculator;
            }
        }
        return pireAxe != null ? pireAxe.getLabelErreur() : "Critères généraux insuffisants";
    }

    /**
     * Méthode utilitaire interne pour extraire un score spécifique de manière isolée.
     */
    private double getScoreSafe(String axeCode, DossierDto d, AnalyseDossier a, MatchingResult m, ScoringConfig c) {
        return calculators.stream()
                .filter(calc -> calc.getAxeCode().equals(axeCode))
                .map(calc -> calc.calculate(d, a, m, c))
                .findFirst()
                .orElse(0.0);
    }

    /** Configuration de secours */
    private ScoringConfig createDefaultConfig() {
        ScoringConfig c = new ScoringConfig();
        c.setPoidsA_faisabilite(0.25);
        c.setPoidsB_rentabilite(0.25);
        c.setPoidsC_risques(0.25);
        c.setPoidsD_concurrence(0.15);
        c.setPoidsE_conformite(0.10);
        c.setSeuilNoGo(30.0);
        c.setSeuilGoConditionnel(50.0);
        c.setSeuilGoFort(70.0);
        c.setTjmMinEgis(400.0);
        c.setTjmMaxEgis(1200.0);
        return c;
    }
}