package tn.rihab.analysteservice.matching.matchers;

import com.fasterxml.jackson.databind.ObjectMapper;
import tn.rihab.analysteservice.dto.DossierDto;
import tn.rihab.analysteservice.dto.ia.RequirementsResponseDto;
import tn.rihab.analysteservice.model.ExpertProfil;
import tn.rihab.analysteservice.repository.ExpertProfilRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Compare les profils d'experts requis dans le TDR
 * avec les experts disponibles dans le référentiel Egis.
 * Version optimisée avec Jackson pour standardiser la Phase 3.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExpertsMatcher {

    private final ExpertProfilRepository expertRepo;
    private final ObjectMapper objectMapper; // 🚀 Injecté par Spring pour une sérialisation robuste

    // 🚀 Structure pour représenter proprement chaque ligne du détail JSON
    public record ExpertMatchDetail(String profil, String expert, boolean disponible) {}

    public record ExpertsMatchResult(
            double tauxCouverture,
            String expertsDetailJson
    ) {}

    /**
     * @param expertsRequis Liste des profils requis (extraits du TDR par Claude)
     * @param dossier       Dossier Phase 1 — pour la période de la mission
     */
    public ExpertsMatchResult match(
            List<RequirementsResponseDto.ExpertRequisDto> expertsRequis,
            DossierDto dossier) {

        if (expertsRequis == null || expertsRequis.isEmpty()) {
            return new ExpertsMatchResult(0.5, "[]");
        }

        // Période de disponibilité estimée depuis la date limite de soumission
        LocalDate debutEstime = dossier.getDtLimSoum() != null
                ? dossier.getDtLimSoum().plusMonths(2)   // démarrage estimé 2 mois après soumission
                : LocalDate.now().plusMonths(3);

        int dureeMois = dossier.getHommesMois() != null
                ? (int) Math.ceil(dossier.getHommesMois() / 2.0) // estimation grossière
                : 12;

        LocalDate finEstimee = debutEstime.plusMonths(dureeMois);

        List<ExpertProfil> disponibles = expertRepo.findDisponibles(debutEstime, finEstimee);

        int couverts = 0;
        // 🚀 On remplace le StringBuilder par une liste d'objets Java typés
        List<ExpertMatchDetail> detailsList = new ArrayList<>();

        for (RequirementsResponseDto.ExpertRequisDto requis : expertsRequis) {
            String roleRecherche = requis.getRole() != null
                    ? requis.getRole().toLowerCase() : "";

            // Chercher un expert disponible dont les spécialités matchent le rôle requis
            ExpertProfil matched = disponibles.stream()
                    .filter(e -> e.getSpecialites() != null && e.getSpecialites().stream()
                            .anyMatch(s -> s.toLowerCase().contains(roleRecherche)
                                    || roleRecherche.contains(s.toLowerCase())))
                    .findFirst()
                    .orElse(null);

            boolean couvert = matched != null;
            if (couvert) {
                couverts++;
            }

            // 🚀 Ajout propre dans la liste d'objets
            detailsList.add(new ExpertMatchDetail(
                    requis.getRole(),
                    matched != null ? matched.getNom() : "Non identifié",
                    couvert
            ));
        }

        // 🚀 Sérialisation automatisée et sécurisée via Jackson
        String expertsDetailJson = "[]";
        try {
            expertsDetailJson = objectMapper.writeValueAsString(detailsList);
        } catch (Exception e) {
            log.error("[ExpertsMatcher] Erreur lors de la sérialisation des détails d'experts", e);
        }

        double taux = (double) couverts / expertsRequis.size();
        log.info("[ExpertsMatcher] {}/{} profils couverts ({:.0f}%)",
                couverts, expertsRequis.size(), taux * 100);

        return new ExpertsMatchResult(taux, expertsDetailJson);
    }
}