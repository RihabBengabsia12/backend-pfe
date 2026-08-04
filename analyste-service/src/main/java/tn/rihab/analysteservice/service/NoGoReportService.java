package tn.rihab.analysteservice.service;

import tn.rihab.analysteservice.dto.DossierDto;
import tn.rihab.analysteservice.client.IaServiceClient;
import tn.rihab.analysteservice.dto.ia.*;
import tn.rihab.analysteservice.model.*;
import tn.rihab.analysteservice.repository.NoGoReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Génère le rapport No-Go narratif (Phase 2).
 *
 * Déclenché quand :
 *  - Le score P-Win est sous le seuil (décision auto = "NO_GO")
 *  - L'utilisateur confirme le No-Go via POST /scoring/{id}/confirm-nogo
 *
 * Produit :
 *  - NoGoReport (entité JPA) avec l'analyse narrative et les motifs principaux
 *  - Export DOCX via DocumentExportService (stocké sur MinIO)
 *
 * Le rapport contient :
 *  - Score P-Win avec décomposition par axe
 *  - 3-5 motifs principaux (axes/champs les plus pénalisants)
 *  - Analyse narrative 200-300 mots générée par Claude
 *  - Section "Forçage" si l'utilisateur veut passer outre (géré côté front)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NoGoReportService {

    private final IaServiceClient        iaClient;
    private final DocumentExportService  exportService;
    private final NoGoReportRepository   noGoRepo;

    /**
     * Génère et persiste le rapport No-Go.
     *
     * @param dossierId ID du dossier
     * @param dossier   Données Phase 1
     * @param pwin      Score P-Win calculé
     * @param analyse   Données Phase 2 (risques)
     * @return NoGoReport persisté
     */
    @Transactional
    public NoGoReport generate(UUID dossierId, DossierDto dossier,
                               PwinScore pwin, AnalyseDossier analyse) {
        log.info("[NoGoReport] Génération rapport No-Go — dossier {} (P-Win={:.1f}%)",
                dossierId, pwin.getScoreGlobal());

        // Construire le contexte pour Claude
        NoGoContextDto context = NoGoContextDto.builder()
                .dossierId(dossierId)
                .intituleOffre(dossier.getIntituleOffre())
                .client(dossier.getClient())
                .pays(dossier.getPays())
                .pwinScore(pwin.getScoreGlobal())
                .scoreA(pwin.getScoreA())
                .scoreB(pwin.getScoreB())
                .scoreC(pwin.getScoreC())
                .scoreD(pwin.getScoreD())
                .scoreE(pwin.getScoreE())
                .decisionAuto(pwin.getDecisionAuto())
                .risques(buildRisquesContexte(analyse))
                .motifPrincipal(pwin.getMotifNogo())
                .build();

        // Générer l'analyse narrative via Claude
        NoGoReportResponseDto response = iaClient.generateNogoReport(context);

        // Identifier les motifs principaux (axes les plus pénalisants)
        String motifsPrincipaux = buildMotifsPrincipaux(pwin, analyse);

        // Persister le rapport
        NoGoReport rapport = noGoRepo.findByDossierId(dossierId)
                .orElse(NoGoReport.builder().dossierId(dossierId).build());

        rapport.setPwinScore(pwin.getScoreGlobal());
        rapport.setMotifsPrincipaux(motifsPrincipaux);
        rapport.setAnalyseNarrative(response.getAnalyseNarrative());

        NoGoReport saved = noGoRepo.save(rapport);

        // Exporter en DOCX et stocker sur MinIO
        try {
            String docxPath = exportService.exportNoGoReport(saved, dossier);
            saved.setDocxPath(docxPath);
            saved = noGoRepo.save(saved);
            log.info("[NoGoReport] DOCX généré : {}", docxPath);
        } catch (Exception e) {
            log.warn("[NoGoReport] Export DOCX échoué (non bloquant) : {}", e.getMessage());
        }

        return saved;
    }

    public NoGoReport getByDossierId(UUID dossierId) {
        return noGoRepo.findByDossierId(dossierId)
                .orElseThrow(() -> new IllegalArgumentException("Rapport No-Go non trouvé : " + dossierId));
    }

    // ── Utilitaires ────────────────────────────────────────────────────────────

    private List<ApoGenerationContextDto.RisqueContexte> buildRisquesContexte(AnalyseDossier analyse) {
        List<ApoGenerationContextDto.RisqueContexte> risques = new ArrayList<>();
        addRisque(risques, "RISQUE_PAYS_SECURITE",           analyse.getRisquePaysSecurite());
        addRisque(risques, "RISQUES_FINANCIERS",             analyse.getRisquesFinanciers());
        addRisque(risques, "PENALITES",                      analyse.getPenalites());
        addRisque(risques, "EXIGENCES_TDR_INACCEPTABLES",   analyse.getExigencesTdrInacceptables());
        addRisque(risques, "GARANTIES_ASSURANCES_ELEVEES",  analyse.getGarantiesAssurancesElevees());
        addRisque(risques, "TAILLE_DISPERSION",             analyse.getTailleDispersion());
        addRisque(risques, "FRAIS_DIVERS_ELEVES",           analyse.getFraisDiversEleves());
        addRisque(risques, "BUDGET_FAIBLE_HM_LIMITES",      analyse.getBudgetFaibleHmLimites());
        addRisque(risques, "PARTICIPATION_LOCALE_EXCESSIVE", analyse.getParticipationLocaleExcessive());
        addRisque(risques, "FISCALITE_NON_MAITRISEE",       analyse.getFiscaliteNonMaitrisee());
        return risques;
    }

    private void addRisque(List<ApoGenerationContextDto.RisqueContexte> list,
                           String nom, String valeur) {
        if (valeur == null) return;
        int idx = valeur.indexOf("||");
        String niveau = idx >= 0 ? valeur.substring(0, idx).trim() : valeur.trim();
        String justif = idx >= 0 ? valeur.substring(idx + 2).trim() : "";
        list.add(ApoGenerationContextDto.RisqueContexte.builder()
                .nom(nom).niveau(niveau).justification(justif).build());
    }

    private String buildMotifsPrincipaux(PwinScore pwin, AnalyseDossier analyse) {
        StringBuilder sb = new StringBuilder("[");
        if (Boolean.TRUE.equals(pwin.getRisqueRedhibitoire())) {
            sb.append(String.format(
                    "{\"axe\":\"C\",\"champ\":\"%s\",\"niveau\":\"Rédhibitoire\",\"poids\":\"critique\"}",
                    pwin.getRisqueRedhibitoireChamp()));
        } else {
            // Les 3 axes les plus faibles
            java.util.Map<String, Double> axes = java.util.Map.of(
                    "A_Faisabilite", pwin.getScoreA() != null ? pwin.getScoreA() : 0.5,
                    "B_Rentabilite", pwin.getScoreB() != null ? pwin.getScoreB() : 0.5,
                    "C_Risques",     pwin.getScoreC() != null ? pwin.getScoreC() : 0.5,
                    "D_Concurrence", pwin.getScoreD() != null ? pwin.getScoreD() : 0.5,
                    "E_Conformite",  pwin.getScoreE() != null ? pwin.getScoreE() : 0.5
            );
            axes.entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByValue())
                    .limit(3)
                    .forEach(e -> {
                        if (sb.length() > 1) sb.append(",");
                        sb.append(String.format(
                                "{\"axe\":\"%s\",\"score\":\"%.0f%%\",\"poids\":\"important\"}",
                                e.getKey(), e.getValue() * 100));
                    });
        }
        return sb.append("]").toString();
    }
}