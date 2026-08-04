package tn.rihab.analysteservice.scoring.calculators;

import tn.rihab.analysteservice.dto.DossierDto;
import tn.rihab.analysteservice.model.AnalyseDossier;
import tn.rihab.analysteservice.model.MatchingResult;
import tn.rihab.analysteservice.model.ScoringConfig;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Axe E — Conformité & alignement stratégique (poids 10%).
 * Version Senior intégrée au moteur polymorphique.
 */
@Component
public class AxeConformiteCalculator implements AxeCalculator {

    // Référentiels dictionnaire en mémoire RAM (Statiques et Immuables)
    private static final Set<String> BAILLEURS_CONNUS = Set.of(
            "banque mondiale", "bm", "afd", "bei", "union européenne", "ue", "fed",
            "bad", "adb", "boad", "fad", "fonds africain de développement"
    );

    private static final Set<String> LANGUES_MAITRISEES = Set.of(
            "français", "francais", "anglais", "arabic", "arabe", "espagnol", "espanol"
    );

    // ── CONFIGURATION ET MÉTADONNÉES DYNAMIQUES ──────────────────────────────

    @Override
    public String getAxeCode() {
        return "AXE_E";
    }

    @Override
    public String getLabelErreur() {
        return "Conformité insuffisante (bailleur inconnu, langue non maîtrisée ou désalignement stratégique)";
    }

    @Override
    public double getPoids(ScoringConfig config) {
        return config.getPoidsE_conformite();
    }

    // ── LOGIQUE DE CALCUL DU SCORE AXE E ─────────────────────────────────────

    @Override
    public double calculate(DossierDto dossier, AnalyseDossier analyse, MatchingResult matching, ScoringConfig config) {
        double score = 0.0;

        // 1. Validation du Bailleur extrait par l'IA
        if (dossier != null && dossier.getBailleurs() != null && !dossier.getBailleurs().isBlank()) {
            String bailleurTexte = dossier.getBailleurs().toLowerCase();
            boolean bailleurConnu = BAILLEURS_CONNUS.stream()
                    .anyMatch(bailleurTexte::contains);

            score += bailleurConnu ? 0.30 : 0.10;
        } else {
            score += 0.10; // Score plancher si aucune donnée de bailleur
        }

        // 2. Validation de la Langue extraite par l'IA
        if (dossier != null && dossier.getLangue() != null && !dossier.getLangue().isBlank()) {
            String langueTexte = dossier.getLangue().toLowerCase();
            boolean langueMaitrisee = LANGUES_MAITRISEES.stream()
                    .anyMatch(langueTexte::contains);

            score += langueMaitrisee ? 0.30 : 0.05;
        } else {
            score += 0.15; // Score neutre si non renseigné
        }

        // 3. Validation de l'Alignement stratégique (issu du composant de Matching)
        if (matching != null && matching.getAlignementStrategique() != null && !matching.getAlignementStrategique().isBlank()) {
            score += switch (matching.getAlignementStrategique().trim().toLowerCase()) {
                case "oui"     -> 0.40;
                case "partiel" -> 0.20;
                default        -> 0.05; // "Non" ou désaligné
            };
        } else {
            score += 0.20; // Score neutre si le matching n'a pas pu évaluer l'alignement
        }

        // Sécurité critique : On borne strictement la note finale de l'axe entre 0.0 et 1.0
        return Math.max(0.0, Math.min(1.0, score));
    }
}