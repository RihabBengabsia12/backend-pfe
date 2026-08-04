package tn.rihab.analysteservice.scoring.calculators;

import lombok.AllArgsConstructor;
import lombok.Getter;
import tn.rihab.analysteservice.dto.DossierDto;
import tn.rihab.analysteservice.model.AnalyseDossier;
import tn.rihab.analysteservice.model.MatchingResult;
import tn.rihab.analysteservice.model.ScoringConfig;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Axe C — Évaluation et maîtrise des risques critiques (poids 25%).
 * Version Senior intégrée au moteur polymorphique.
 */
@Component
public class AxeRisquesCalculator implements AxeCalculator {

    private static final Map<String, Double> GRILLE = Map.of(
            "Faible",       1.00,
            "Modéré",       0.70,
            "Élevé",        0.30,
            "Rédhibitoire", 0.00
    );

    // ── CONFIGURATION ET MÉTADONNÉES DYNAMIQUES ──────────────────────────────

    @Override
    public String getAxeCode() {
        return "AXE_C";
    }

    @Override
    public String getLabelErreur() {
        return "Risques opérationnels ou contractuels trop élevés";
    }

    @Override
    public double getPoids(ScoringConfig config) {
        return config.getPoidsC_risques();
    }

    // ── LE PONT POUR L'INTERFACE COMMUNE ─────────────────────────────────────

    @Override
    public double calculate(DossierDto dossier, AnalyseDossier analyse, MatchingResult matching, ScoringConfig config) {
        // Redirection vers le calculateur natif pour récupérer uniquement le score numérique pur
        return this.calculate(analyse).getScore();
    }

    // ── LOGIQUE MÉTIER NATIF ET DÉTAILLÉ (AVEC KILL SWITCH) ──────────────────

    /**
     * Calcule le score complet de l'Axe C et détecte les éléments bloquants ("Rédhibitoire").
     */
    public RisquesResult calculate(AnalyseDossier analyse) {
        // Sécurité critique : si l'objet analyse n'est pas instancié par l'IA ou est null
        if (analyse == null) {
            return new RisquesResult(0.5, false, null); // Retourne un score neutre par défaut
        }

        // Construction ordonnée de la cartographie des 10 risques identifiés par l'IA
        Map<String, String> risques = new LinkedHashMap<>();
        risques.put("RISQUE_PAYS_SECURITE",          analyse.getRisquePaysSecurite());
        risques.put("RISQUES_FINANCIERS",             analyse.getRisquesFinanciers());
        risques.put("PENALITES",                      analyse.getPenalites());
        risques.put("EXIGENCES_TDR_INACCEPTABLES",    analyse.getExigencesTdrInacceptables());
        risques.put("GARANTIES_ASSURANCES_ELEVEES",   analyse.getGarantiesAssurancesElevees());
        risques.put("TAILLE_DISPERSION",              analyse.getTailleDispersion());
        risques.put("FRAIS_DIVERS_ELEVES",            analyse.getFraisDiversEleves());
        risques.put("BUDGET_FAIBLE_HM_LIMITES",       analyse.getBudgetFaibleHmLimites());
        risques.put("PARTICIPATION_LOCALE_EXCESSIVE", analyse.getParticipationLocaleExcessive());
        risques.put("FISCALITE_NON_MAITRISEE",        analyse.getFiscaliteNonMaitrisee());

        double total = 0.0;
        int count    = 0;
        String champRedhibitoire = null;

        for (Map.Entry<String, String> entry : risques.entrySet()) {
            if (entry.getValue() == null) continue;

            String niveau = extractNiveau(entry.getValue());
            double pts    = GRILLE.getOrDefault(niveau, 0.5);

            // Détection du premier risque rédhibitoire (Kill Switch)
            if ("Rédhibitoire".equals(niveau) && champRedhibitoire == null) {
                champRedhibitoire = entry.getKey();
            }

            total += pts;
            count++;
        }

        // Si aucun risque n'est exploitable dans l'analyse
        if (count == 0) {
            return new RisquesResult(0.5, false, null);
        }

        // Règle absolue du métier : 1 seul risque Rédhibitoire force instantanément l'Axe C à 0.0
        if (champRedhibitoire != null) {
            return new RisquesResult(0.0, true, champRedhibitoire);
        }

        double scoreC = total / count;
        return new RisquesResult(Math.max(0.0, Math.min(1.0, scoreC)), false, null);
    }

    /**
     * Extrait le niveau textuel depuis le format composite de l'IA : "NIVEAU||justification"
     */
    private String extractNiveau(String valeur) {
        if (valeur == null || valeur.isBlank()) return "Inconnu";
        int idx = valeur.indexOf("||");
        return idx >= 0 ? valeur.substring(0, idx).trim() : valeur.trim();
    }

    // ── DTO INTERNE POUR TRANSPORTER LE RÉSULTAT DU SOUFFLET RÉSIDUEL ─────────

    @Getter
    @AllArgsConstructor
    public static class RisquesResult {
        private final double score;
        private final boolean redhibitoire;
        private final String champRedhibitoire;
    }
}