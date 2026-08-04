package tn.rihab.analysteservice.matching.matchers;

import tn.rihab.analysteservice.model.Reference;
import tn.rihab.analysteservice.repository.ReferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Évalue le niveau de la relation commerciale avec le client.
 * Produit [[RELATION_CLIENT]] niveau 1-5.
 *
 * Grille :
 *   1 = Premier contact (jamais travaillé ensemble)
 *   2 = Contact établi (réunion, conférence)
 *   3 = Missions passées (1-2 contrats)
 *   4 = Client fidèle (3+ contrats)
 *   5 = Partenaire stratégique (relation long terme, confiance établie)
 *
 * Source : référentiel des projets passés (table references_projets).
 * Si un projet a le même client → niveau minimum 3.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClientMatcher {

    private final ReferenceRepository referenceRepository;

    public record ClientMatchResult(int niveau, int nbMissions) {}

    /**
     * @param clientNom Nom du client extrait de la DP (ex: "Ministère de l'Eau du Maroc")
     * @return Niveau relation 1-5 + nombre de missions passées
     */
    public ClientMatchResult match(String clientNom) {
        if (clientNom == null || clientNom.isBlank()) {
            return new ClientMatchResult(1, 0);
        }

        List<Reference> toutes = referenceRepository.findByActifTrue();
        String clientLower = clientNom.toLowerCase();

        long nbMissions = toutes.stream()
                .filter(r -> r.getClient() != null
                        && r.getClient().toLowerCase().contains(
                        // Prendre les 3 premiers mots significatifs pour un matching souple
                        clientLower.split("\\s+").length >= 3
                                ? clientLower.split("\\s+")[2]
                                : clientLower))
                .count();

        int niveau;
        if (nbMissions == 0)      niveau = 1;
        else if (nbMissions == 1) niveau = 3;
        else if (nbMissions == 2) niveau = 3;
        else if (nbMissions <= 5) niveau = 4;
        else                      niveau = 5;

        log.info("[ClientMatcher] Client='{}' → {} mission(s) → niveau {}",
                clientNom, nbMissions, niveau);

        return new ClientMatchResult(niveau, (int) nbMissions);
    }
}