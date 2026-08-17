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
    private final tn.rihab.analysteservice.repository.IaAuditLogRepository auditLogRepo;
    private final tn.rihab.analysteservice.client.ProjectServiceClient projectClient;

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
                               PwinScore pwin, AnalyseDossier analyse, String analysteName) {
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

        // Appel à l'IA pour générer le rapport
        NoGoReportResponseDto response = iaClient.generateNogoReport(context);
        
        saveAudit(dossierId, "GENERATION_NOGO_REPORT", response.getStats());

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
            // Le rapport enregistré reste anonymisé. Une copie décapsulée est
            // créée uniquement pour le document final remis à l'utilisateur.
            NoGoReport exportReport = NoGoReport.builder()
                    .id(saved.getId())
                    .dossierId(saved.getDossierId())
                    .pwinScore(saved.getPwinScore())
                    .motifsPrincipaux(decapsulate(dossierId, saved.getMotifsPrincipaux()))
                    .analyseNarrative(decapsulate(dossierId, saved.getAnalyseNarrative()))
                    .docxPath(saved.getDocxPath())
                    .generatedAt(saved.getGeneratedAt())
                    .build();
            String docxPath = exportService.exportNoGoReport(
                    exportReport, decapsulateDossier(dossierId, dossier), analysteName, pwin);
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

    private String decapsulate(UUID dossierId, String text) {
        if (text == null || text.isBlank()) return text;
        try {
            return projectClient.decapsulate(dossierId, text);
        } catch (Exception e) {
            log.error("[NoGoReport] Erreur de décapsulation DLP pour le dossier {}", dossierId, e);
            return text;
        }
    }

    private DossierDto decapsulateDossier(UUID dossierId, DossierDto source) {
        return DossierDto.builder()
                .id(source.getId()).status(source.getStatus()).isPrivate(source.getIsPrivate())
                .pays(decapsulate(dossierId, source.getPays()))
                .intituleOffre(decapsulate(dossierId, source.getIntituleOffre()))
                .numeroReference(decapsulate(dossierId, source.getNumeroReference()))
                .client(decapsulate(dossierId, source.getClient()))
                .bailleurs(decapsulate(dossierId, source.getBailleurs()))
                .budgetGlobal(decapsulate(dossierId, source.getBudgetGlobal()))
                .hommesMois(source.getHommesMois()).dtLimSoum(source.getDtLimSoum())
                .langue(decapsulate(dossierId, source.getLangue()))
                .visiteObl(source.getVisiteObl()).visiteDate(source.getVisiteDate())
                .confObl(source.getConfObl()).confDate(source.getConfDate())
                .arriveBo(source.getArriveBo()).transmission(source.getTransmission())
                .tjmImplicite(source.getTjmImplicite()).joursOuvrables(source.getJoursOuvrables())
                .priorite(source.getPriorite()).confianceP1(source.getConfianceP1())
                .pwinScore(source.getPwinScore()).documentTextPath(source.getDocumentTextPath())
                .apoDocxPath(source.getApoDocxPath()).methodoDocxPath(source.getMethodoDocxPath())
                .rapportPath(source.getRapportPath()).nogoReportPath(source.getNogoReportPath())
                .packZipPath(source.getPackZipPath()).auditReportPath(source.getAuditReportPath())
                .createdAt(source.getCreatedAt()).updatedAt(source.getUpdatedAt())
                .build();
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

    private String formatMotifsForDocx(String jsonMotifs) {
        if (jsonMotifs == null || !jsonMotifs.startsWith("[")) return jsonMotifs;
        try {
            // Very simple JSON parsing for DOCX formatting (without importing heavy libraries just for this)
            StringBuilder text = new StringBuilder();
            String[] items = jsonMotifs.substring(1, jsonMotifs.length() - 1).split("\\},\\{");
            for (String item : items) {
                item = item.replace("{", "").replace("}", "").replace("\"", "");
                String axe = extractJsonValue(item, "axe");
                String champ = extractJsonValue(item, "champ");
                String niveau = extractJsonValue(item, "niveau");
                String score = extractJsonValue(item, "score");
                
                text.append("• Axe ").append(axe.replace("_", " "));
                if (champ != null && !champ.isEmpty()) text.append(" (").append(champ).append(")");
                text.append(" : ");
                if (niveau != null && !niveau.isEmpty()) text.append(niveau);
                if (score != null && !score.isEmpty()) text.append(score);
                text.append("\n");
            }
            return text.toString().trim();
        } catch (Exception e) {
            return jsonMotifs; // Fallback to raw JSON if parsing fails
        }
    }
    
    private String extractJsonValue(String item, String key) {
        String search = key + ":";
        int idx = item.indexOf(search);
        if (idx == -1) return null;
        int end = item.indexOf(",", idx);
        if (end == -1) end = item.length();
        return item.substring(idx + search.length(), end);
    }

    private void saveAudit(UUID dossierId, String actionName, java.util.Map<String, Object> stats) {
        if (stats == null) return;
        try {
            Integer tokenUsage = stats.get("token_usage") != null ? ((Number)stats.get("token_usage")).intValue() : null;
            Integer inputTokens = stats.get("input_tokens") != null ? ((Number)stats.get("input_tokens")).intValue() : null;
            Integer outputTokens = stats.get("output_tokens") != null ? ((Number)stats.get("output_tokens")).intValue() : null;
            Integer processingTimeMs = stats.get("processing_time_ms") != null ? ((Number)stats.get("processing_time_ms")).intValue() : null;
            Double estimatedCost = stats.get("estimated_cost") != null ? ((Number)stats.get("estimated_cost")).doubleValue() : null;
            
            auditLogRepo.save(tn.rihab.analysteservice.model.IaAuditLog.builder()
                    .dossierId(dossierId)
                    .actionName(actionName)
                    .tokenUsage(tokenUsage)
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .processingTimeMs(processingTimeMs)
                    .estimatedCost(estimatedCost)
                    .rawJson("{}")
                    .build());
        } catch (Exception e) {
            log.warn("Erreur de sauvegarde de l'audit pour {}", actionName, e);
        }
    }
}
