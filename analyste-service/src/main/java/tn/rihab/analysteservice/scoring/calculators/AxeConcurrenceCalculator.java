package tn.rihab.analysteservice.scoring.calculators;

import tn.rihab.analysteservice.dto.DossierDto;
import tn.rihab.analysteservice.model.AnalyseDossier;
import tn.rihab.analysteservice.model.MatchingResult;
import tn.rihab.analysteservice.model.ScoringConfig;
import org.springframework.stereotype.Component;

/**
 * Axe D — Position concurrentielle (poids 15%).
 * Version Senior intégrée au moteur polymorphique.
 */
@Component
public class AxeConcurrenceCalculator implements AxeCalculator {

    @Override
    public String getAxeCode() {
        return "AXE_D";
    }

    @Override
    public String getLabelErreur() {
        return "Position concurrentielle défavorable ou shortlist déséquilibrée";
    }

    @Override
    public double getPoids(ScoringConfig config) {
        return config.getPoidsD_concurrence();
    }

    @Override
    public double calculate(DossierDto dossier, AnalyseDossier analyse, MatchingResult matching, ScoringConfig config) {

        // Extraction locale et sécurisée de la shortlist depuis l'analyse pour Angular
        String shortlistEquilibree = (analyse != null && analyse.getShortlistEquilibree() != null)
                ? analyse.getShortlistEquilibree().trim()
                : "NA";

        double score = 0.0;

        // 1. Relation client (niveau 1-5)
        if (matching != null && matching.getRelationClientNiveau() != null) {
            int niveau = matching.getRelationClientNiveau();

            // Sécurité : on borne le niveau entre 1 et 5 pour éviter tout calcul aberrant
            int niveauBorne = Math.max(1, Math.min(5, niveau));

            score += 0.65 * (niveauBorne / 5.0);

            // Bonus si client fidèle (niveau >= 4)
            if (niveauBorne >= 4) {
                score += 0.10;
            }
        } else {
            score += 0.20; // Score neutre si la relation client est inconnue
        }

        // 2. Shortlist équilibrée
        if (!shortlistEquilibree.isBlank()) {
            score += switch (shortlistEquilibree.toLowerCase()) {
                case "oui" -> 0.25;
                case "na", "n/a" -> 0.10;
                default -> 0.0; // "Non" ou shortlist explicitement déséquilibrée
            };
        } else {
            score += 0.10; // Score neutre si le champ est vide ou non renseigné
        }

        // Borner le résultat final de l'axe de manière stricte entre 0.0 et 1.0
        return Math.max(0.0, Math.min(1.0, score));
    }
}