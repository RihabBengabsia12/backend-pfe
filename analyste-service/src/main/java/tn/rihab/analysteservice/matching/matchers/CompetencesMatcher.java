package tn.rihab.analysteservice.matching.matchers;

import com.fasterxml.jackson.databind.ObjectMapper;
import tn.rihab.analysteservice.model.Competence;
import tn.rihab.analysteservice.repository.CompetenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Compare les exigences de compétences extraites de la DP
 * avec le référentiel Egis (table competences).
 * Version optimisée avec Jackson pour éliminer la sérialisation manuelle.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CompetencesMatcher {

    private final CompetenceRepository competenceRepository;
    private final ObjectMapper objectMapper; // 🚀 Injecté automatiquement par Spring pour un JSON robuste

    // 🚀 Structure propre pour représenter chaque ligne de détail dans le JSON final
    public record CompetenceDetail(String exigence, String niveau, double score) {}

    public record CompetencesMatchResult(double taux, String detailJson) {}

    /**
     * @param exigencesDP Secteurs/compétences requis extraits de la DP par Claude
     * @return Taux de couverture 0.0–1.0 + détail JSON propre
     */
    public CompetencesMatchResult match(List<String> exigencesDP) {
        List<Competence> referentiel = competenceRepository.findByActifTrue();

        if (exigencesDP == null || exigencesDP.isEmpty()) {
            return new CompetencesMatchResult(0.5, "[]"); // neutre si non renseigné
        }

        double total = 0.0;
        // 🚀 On remplace le StringBuilder par une vraie liste d'objets fortement typés
        List<CompetenceDetail> detailsList = new ArrayList<>();

        for (String exigenceRaw : exigencesDP) {
            if (exigenceRaw == null || exigenceRaw.isBlank()) continue;

            String exigence = exigenceRaw.toLowerCase().trim();
            double meilleurScore = 0.0;
            String niveauMatche = "AUCUN";

            for (Competence comp : referentiel) {
                // Match sur le domaine
                boolean matchDomaine = comp.getDomaine() != null
                        && comp.getDomaine().toLowerCase().contains(exigence);

                // Match sur un mot-clé
                boolean matchMotCle = comp.getMotsCles() != null
                        && comp.getMotsCles().stream()
                        .anyMatch(mk -> mk.toLowerCase().contains(exigence)
                                || exigence.contains(mk.toLowerCase()));

                if (matchDomaine || matchMotCle) {
                    double scoreNiveau = niveauToScore(comp.getNiveau());
                    if (scoreNiveau > meilleurScore) {
                        meilleurScore = scoreNiveau;
                        niveauMatche = comp.getNiveau();
                    }
                }
            }

            total += meilleurScore;
            // 🚀 Ajout direct de l'enregistrement dans la liste
            detailsList.add(new CompetenceDetail(exigenceRaw, niveauMatche, meilleurScore));
        }

        // 🚀 Sérialisation unifiée et sans failles
        String jsonResult = "[]";
        try {
            jsonResult = objectMapper.writeValueAsString(detailsList);
        } catch (Exception e) {
            log.error("[CompetencesMatcher] Échec de la génération du JSON de détails", e);
        }

        double taux = total / exigencesDP.size();
        log.info("[CompetencesMatcher] {} exigences → taux couverture {:.0f}%",
                exigencesDP.size(), taux * 100);

        return new CompetencesMatchResult(taux, jsonResult);
    }

    private double niveauToScore(String niveau) {
        if (niveau == null) return 0.5;
        return switch (niveau.toUpperCase()) {
            case "DEBUTANT"   -> 0.40;
            case "CONFIRME"   -> 0.70;
            case "EXPERT"     -> 0.90;
            case "REFERENCE"  -> 1.00;
            default           -> 0.50;
        };
    }
}