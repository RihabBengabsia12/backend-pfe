package tn.rihab.analysteservice.scoring.calculators;

import tn.rihab.analysteservice.dto.DossierDto;
import tn.rihab.analysteservice.model.AnalyseDossier;
import tn.rihab.analysteservice.model.MatchingResult;
import tn.rihab.analysteservice.model.ScoringConfig;
import org.springframework.stereotype.Component;

/**
 * Axe B — Rentabilité financière (poids 25%).
 * Version Senior intégrée au moteur polymorphique.
 */
@Component
public class AxeRentabiliteCalculator implements AxeCalculator {

    // ── CONFIGURATION ET MÉTADONNÉES DYNAMIQUES ──────────────────────────────

    @Override
    public String getAxeCode() {
        return "AXE_B";
    }

    @Override
    public String getLabelErreur() {
        return "Rentabilité financière insuffisante (TJM estimé trop bas ou risques de frais/pénalités trop lourds)";
    }

    @Override
    public double getPoids(ScoringConfig config) {
        return config.getPoidsB_rentabilite();
    }

    // ── LOGIQUE DE CALCUL DU SCORE AXE B ─────────────────────────────────────

    @Override
    public double calculate(DossierDto dossier, AnalyseDossier analyse, MatchingResult matching, ScoringConfig config) {
        double score = 0.0;

        // 1. TJM implicite vs seuils configurables Egis
        if (dossier != null && dossier.getTjmImplicite() != null && dossier.getTjmImplicite() > 0) {
            double tjm = dossier.getTjmImplicite();

            // Récupération sécurisée des seuils de la configuration dynamique
            double tjmMin = (config != null) ? config.getTjmMinEgis() : 400.0;
            double tjmMax = (config != null) ? config.getTjmMaxEgis() : 1200.0;

            if (tjm >= tjmMin) {
                // Score proportionnel plafonné à 0.60
                double ratio = (tjmMax > tjmMin) ? Math.min((tjm - tjmMin) / (tjmMax - tjmMin), 1.0) : 1.0;
                score += 0.30 + 0.30 * ratio;
            } else {
                // TJM sous le seuil minimal accepté par Egis : score très faible
                score += 0.05 * (tjm / tjmMin);
            }
        }

        // 2. Évaluation de l'impact des pénalités contractuelles
        if (analyse != null && analyse.getPenalites() != null) {
            String niveau = extractNiveau(analyse.getPenalites());
            score += switch (niveau) {
                case "Faible"   -> 0.20;
                case "Modéré"   -> 0.12;
                case "Élevé"    -> 0.05;
                default         -> 0.0; // Rédhibitoire ou Inconnu
            };
        }

        // 3. Évaluation de l'impact des frais divers élevés
        if (analyse != null && analyse.getFraisDiversEleves() != null) {
            String niveau = extractNiveau(analyse.getFraisDiversEleves());
            score += switch (niveau) {
                case "Faible" -> 0.20;
                case "Modéré" -> 0.12;
                case "Élevé"  -> 0.04;
                default       -> 0.0; // Rédhibitoire ou Inconnu
            };
        }

        // Sécurité critique : Borner strictement la note entre 0.0 et 1.0
        return clamp(score);
    }

    /**
     * Extrait le niveau de risque depuis la structure textuelle de l'IA : "NIVEAU||justification"
     */
    private String extractNiveau(String valeur) {
        if (valeur == null || valeur.isBlank()) return "Inconnu";
        int idx = valeur.indexOf("||");
        return idx >= 0 ? valeur.substring(0, idx).trim() : valeur.trim();
    }

    private double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}