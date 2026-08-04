package tn.rihab.analysteservice.matching.matchers;

import com.fasterxml.jackson.databind.ObjectMapper;
import tn.rihab.analysteservice.model.Reference;
import tn.rihab.analysteservice.repository.ReferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Compare les exigences de références de la DP
 * avec le référentiel des projets passés d'Egis.
 * Version optimisée avec Jackson pour éliminer la sérialisation manuelle.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReferencesMatcher {

    private final ReferenceRepository referenceRepository;
    private final ObjectMapper objectMapper; // 🚀 Injecté automatiquement par Spring pour un JSON robuste

    // 🚀 Structure pour représenter proprement l'analyse du gap de références dans le JSON
    public record ReferenceGapDetail(String exigence, String reference, String couvert) {}

    public record ReferencesMatchResult(String gapRefsJson, List<Reference> refsDisponibles) {}

    /**
     * @param secteur  Secteur technique détecté (ex: "Eau & Assainissement")
     * @param pays     Pays d'exécution
     * @param budget   Budget global (€) — approximatif, extrait du dossier
     * @return GAP_REFS JSON propre + liste des références disponibles
     */
    public ReferencesMatchResult match(String secteur, String pays, Double budget) {
        List<Reference> toutes = referenceRepository.findByActifTrue();

        List<Reference> matchees  = new ArrayList<>();
        List<Reference> partielles = new ArrayList<>();

        int anneeMin = java.time.LocalDate.now().getYear() - 10;

        for (Reference ref : toutes) {
            boolean matchSecteur = secteur != null && ref.getSecteur() != null
                    && ref.getSecteur().toLowerCase().contains(secteur.toLowerCase().split("&")[0].trim());

            boolean matchAnnee = ref.getAnnee() == null || ref.getAnnee() >= anneeMin;

            boolean matchBudget = budget == null || ref.getBudgetEuros() == null
                    || (ref.getBudgetEuros() >= budget * 0.3 && ref.getBudgetEuros() <= budget * 3.0);

            boolean matchPays = pays != null && ref.getPays() != null
                    && ref.getPays().equalsIgnoreCase(pays.trim());

            if (matchSecteur && matchAnnee) {
                if (matchPays || matchBudget) {
                    matchees.add(ref);
                } else {
                    partielles.add(ref);
                }
            }
        }

        // 🚀 On remplace le StringBuilder artisanal par une liste d'objets fortement typés
        List<ReferenceGapDetail> gapList = new ArrayList<>();
        String exigenceLabel = secteur != null ? secteur : "Non spécifié";

        if (!matchees.isEmpty()) {
            Reference best = matchees.get(0);
            gapList.add(new ReferenceGapDetail(
                    exigenceLabel,
                    best.getTitre() + " (" + (best.getAnnee() != null ? best.getAnnee() : "Année inconnue") + ")",
                    "OUI"
            ));
        } else if (!partielles.isEmpty()) {
            Reference best = partielles.get(0);
            gapList.add(new ReferenceGapDetail(
                    exigenceLabel,
                    best.getTitre() + " (" + (best.getAnnee() != null ? best.getAnnee() : "Année inconnue") + ")",
                    "PARTIEL"
            ));
        } else {
            gapList.add(new ReferenceGapDetail(exigenceLabel, "Aucune référence disponible", "NON"));
        }

        // 🚀 Sérialisation sécurisée et unifiée via Jackson
        String gapRefsJson = "[]";
        try {
            gapRefsJson = objectMapper.writeValueAsString(gapList);
        } catch (Exception e) {
            log.error("[ReferencesMatcher] Erreur lors de la génération du JSON de gap", e);
        }

        List<Reference> disponibles = new ArrayList<>(matchees);
        disponibles.addAll(partielles);

        log.info("[ReferencesMatcher] secteur={} pays={} → {} match(s) exact(s), {} partiel(s)",
                secteur, pays, matchees.size(), partielles.size());

        return new ReferencesMatchResult(gapRefsJson, disponibles);
    }
}