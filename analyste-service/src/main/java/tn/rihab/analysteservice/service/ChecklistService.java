package tn.rihab.analysteservice.service;

import tn.rihab.analysteservice.client.IaServiceClient;
import tn.rihab.analysteservice.dto.ia.ChecklistRequestDto;
import tn.rihab.analysteservice.dto.ia.ChecklistResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Génère la checklist des pièces administratives exigées selon le bailleur.
 *
 * Règles appliquées :
 *   Banque Mondiale → règles SBD (Standard Bidding Documents)
 *   AFD             → règles CPAR
 *   Union Européenne (FED/DEVCO) → règles PRAG
 *   Autre / inconnu → checklist standard générique
 *
 * Certaines pièces sont toujours requises (mandatoires universelles),
 * d'autres dépendent du bailleur ou du type de marché.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChecklistService {

    private final IaServiceClient iaClient;

    /**
     * Génère la checklist en deux passes :
     *   1. Règles fixes codées en dur (toujours correctes, instantanées)
     *   2. Enrichissement via Claude pour les règles spécifiques au contexte
     *
     * @param bailleurs Bailleurs détectés (ex: "Banque Mondiale, AFD")
     * @param pays      Pays d'exécution (pour règles locales éventuelles)
     * @return Liste des pièces pointées avec obligatoire/optionnel
     */
    public ChecklistResponseDto generate(String bailleurs, String pays) {
        log.info("[Checklist] Génération pièces pointées — bailleur={} pays={}", bailleurs, pays);

        // Pièces universelles — toujours requises quel que soit le bailleur
        List<ChecklistResponseDto.PieceDto> pieces = new ArrayList<>(universalPieces());

        // Pièces spécifiques selon le bailleur
        if (bailleurs != null) {
            String b = bailleurs.toLowerCase();
            if (b.contains("banque mondiale") || b.contains("world bank") || b.contains("bm")) {
                pieces.addAll(worldBankPieces());
            }
            if (b.contains("afd") || b.contains("agence française de développement")) {
                pieces.addAll(afdPieces());
            }
            if (b.contains("union européenne") || b.contains("ue") || b.contains("fed")
                    || b.contains("devco") || b.contains("fed")) {
                pieces.addAll(euPieces());
            }
            if (b.contains("bad") || b.contains("banque africaine") || b.contains("adb")) {
                pieces.addAll(badPieces());
            }
        }

        // Enrichissement Claude pour cas particuliers ou règles locales
        try {
            ChecklistResponseDto claudeResp = iaClient.generateChecklist(
                    ChecklistRequestDto.builder()
                            .bailleurs(bailleurs)
                            .pays(pays)
                            .build());
            if (claudeResp != null && claudeResp.getPieces() != null) {
                // Ajouter uniquement les pièces non déjà listées
                claudeResp.getPieces().stream()
                        .filter(p -> pieces.stream().noneMatch(
                                existing -> existing.getNom().equalsIgnoreCase(p.getNom())))
                        .forEach(pieces::add);
            }
        } catch (Exception e) {
            log.warn("[Checklist] Enrichissement Claude échoué (non bloquant) : {}", e.getMessage());
        }

        log.info("[Checklist] {} pièces générées", pieces.size());
        return ChecklistResponseDto.builder().pieces(pieces).build();
    }

    // ── Pièces universelles ─────────────────────────────────────────────────────

    private List<ChecklistResponseDto.PieceDto> universalPieces() {
        return List.of(
                piece("Offre technique", "Document principal décrivant la méthodologie et l'équipe", true, "STANDARD"),
                piece("Offre financière",    "Budget détaillé avec taux journaliers et frais", true, "STANDARD"),
                piece("Lettre de soumission","Lettre officielle signée par le représentant légal",  true, "STANDARD"),
                piece("Statuts de la société","Copie certifiée conforme des statuts", true, "STANDARD"),
                piece("Extrait K-bis ou équivalent", "Extrait du registre du commerce < 3 mois", true, "STANDARD"),
                piece("Attestations fiscales et sociales", "Moins de 3 mois à la date de soumission", true, "STANDARD"),
                piece("CV des experts clés",  "Format standard CV, signés par les experts",           true, "STANDARD"),
                piece("Diplômes et certifications des experts", "Copies certifiées conformes",        true, "STANDARD"),
                piece("Références de missions similaires",      "Lettres de référence ou attestations client", true, "STANDARD"),
                piece("Déclaration d'intégrité", "Attestation anti-corruption signée",               true, "STANDARD"),
                piece("Pouvoirs du signataire", "Délégation de signature si représentant ≠ DG",    true, "STANDARD"),
                piece("Garantie de soumission (si exigée)", "Caution bancaire selon montant spécifié", false, "STANDARD")
        );
    }

    // ── Pièces Banque Mondiale (SBD) ───────────────────────────────────────────

    private List<ChecklistResponseDto.PieceDto> worldBankPieces() {
        return List.of(
                piece("Formulaire d'offre SBD",    "Formulaire standard Banque Mondiale rempli",     true,  "BM-SBD"),
                piece("Déclaration de nationalité","Confirme l'éligibilité selon règles BM",          true,  "BM-SBD"),
                piece("Conflict of Interest (COI) form", "Formulaire déclaration conflit d'intérêts", true, "BM-SBD"),
                piece("Liste des contrats BM en cours", "Déclaration des projets BM actifs",         true,  "BM-SBD"),
                piece("Rapport financier audité (3 ans)", "Bilan et compte de résultat audités",     true,  "BM-SBD"),
                piece("Plan de gestion environnementale et sociale (PGES)", "Si requis par le projet", false,"BM-SBD")
        );
    }

    // ── Pièces AFD (CPAR) ──────────────────────────────────────────────────────

    private List<ChecklistResponseDto.PieceDto> afdPieces() {
        return List.of(
                piece("Note méthodologique AFD",   "Format CPAR / note de présentation spécifique AFD", true, "AFD-CPAR"),
                piece("Déclaration d'absence de conflits d'intérêts AFD", "Formulaire AFD officiel",    true, "AFD-CPAR"),
                piece("Attestation d'assurance RC professionnelle", "Couverture minimale selon le contrat", true, "AFD-CPAR"),
                piece("Charte déontologique AFD", "Engagement sur les principes AFD de responsabilité", false,"AFD-CPAR")
        );
    }

    // ── Pièces Union Européenne (PRAG/DEVCO) ───────────────────────────────────

    private List<ChecklistResponseDto.PieceDto> euPieces() {
        return List.of(
                piece("Formulaire de soumission PRAG", "Formulaire officiel UE/DEVCO signé",           true, "UE-PRAG"),
                piece("Déclaration sur l'honneur UE",  "Attestation éligibilité et absence exclusion",  true, "UE-PRAG"),
                piece("Capacité financière (CF1 ou CF2)", "Chiffre d'affaires moyen justifié",         true, "UE-PRAG"),
                piece("Identification légale (entité légale)", "Formulaire FIF UE validé",              true, "UE-PRAG"),
                piece("Code de conduite des partenaires UE", "Signature engagement partenaires",        false,"UE-PRAG"),
                piece("Rapport OLAF si applicable", "Déclaration absence de fraude UE",                false,"UE-PRAG")
        );
    }

    // ── Pièces BAD (Banque Africaine de Développement) ─────────────────────────

    private List<ChecklistResponseDto.PieceDto> badPieces() {
        return List.of(
                piece("Formulaire BAD / ADB standard", "Formulaire officiel BAD rempli",               true, "BAD"),
                piece("Déclaration BAD anti-corruption", "Engagement anti-corruption BAD",              true, "BAD"),
                piece("Expérience en Afrique subsaharienne", "Références dans la zone BAD",            false,"BAD")
        );
    }

    private ChecklistResponseDto.PieceDto piece(String nom, String desc,
                                                boolean obligatoire, String source) {
        return ChecklistResponseDto.PieceDto.builder()
                .nom(nom).description(desc).obligatoire(obligatoire).source(source).build();
    }
}