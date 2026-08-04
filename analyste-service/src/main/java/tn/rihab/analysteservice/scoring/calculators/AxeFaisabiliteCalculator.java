package tn.rihab.analysteservice.scoring.calculators;

import tn.rihab.analysteservice.dto.DossierDto;
import tn.rihab.analysteservice.model.AnalyseDossier;
import tn.rihab.analysteservice.model.MatchingResult;
import tn.rihab.analysteservice.model.ScoringConfig;
import org.springframework.stereotype.Component;

/**
 * Axe A — Faisabilité technique (poids 25%).
 * Version Senior intégrée au moteur polymorphique.
 */
@Component
public class AxeFaisabiliteCalculator implements AxeCalculator {

    // ── CONFIGURATION ET MÉTADONNÉES DYNAMIQUES ──────────────────────────────

    @Override
    public String getAxeCode() {
        return "AXE_A";
    }

    @Override
    public String getLabelErreur() {
        return "Faisabilité opérationnelle insuffisante (délais de préparation trop courts ou manque d'experts disponibles)";
    }

    @Override
    public double getPoids(ScoringConfig config) {
        return config.getPoidsA_faisabilite();
    }

    // ── LOGIQUE DE CALCUL DU SCORE AXE A ─────────────────────────────────────

    @Override
    public double calculate(DossierDto dossier, AnalyseDossier analyse, MatchingResult matching, ScoringConfig config) {
        // Sécurité si l'objet analyse n'est pas instancié par l'IA
        if (analyse == null) {
            return 0.5; // Retourne un score neutre de secours
        }

        double score = 0.0;

        // 1. Délai de préparation suffisant
        if (analyse.getDelaiPrepSuf() == null || analyse.getDelaiPrepSuf().isBlank()) {
            score += 0.15; // Score neutre si non renseigné
        } else {
            score += "Oui".equalsIgnoreCase(analyse.getDelaiPrepSuf().trim()) ? 0.30 : 0.05;
        }

        // 2. Capacité à tenir les délais (plan de charge interne)
        if (analyse.getCapaciteDelai() == null || analyse.getCapaciteDelai().isBlank()) {
            score += 0.15; // Score neutre si non renseigné
        } else {
            score += "Oui".equalsIgnoreCase(analyse.getCapaciteDelai().trim()) ? 0.25 : 0.05;
        }

        // 3. Taux de couverture des experts disponibles
        if (matching != null && matching.getTauxCouvertureExperts() != null) {
            score += 0.25 * clamp(matching.getTauxCouvertureExperts());
        } else {
            score += 0.125; // Score neutre (la moitié des points alloués)
        }

        // 4. Taux de couverture des compétences sectorielles
        if (matching != null && matching.getTauxCouvertureCompetences() != null) {
            score += 0.20 * clamp(matching.getTauxCouvertureCompetences());
        } else {
            score += 0.10; // Score neutre (la moitié des points alloués)
        }

        return clamp(score);
    }

    /**
     * Borne une valeur de manière stricte entre 0.0 et 1.0.
     */
    private double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}