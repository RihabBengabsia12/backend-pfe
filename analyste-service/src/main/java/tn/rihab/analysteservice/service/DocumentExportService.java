package tn.rihab.analysteservice.service;

import tn.rihab.analysteservice.dto.DossierDto;
import tn.rihab.analysteservice.dto.ia.MethodologieResponseDto;
import tn.rihab.analysteservice.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Génère les fichiers DOCX à partir des templates Word.
 * Version Senior optimisée pour le traitement en mémoire RAM et l'envoi direct vers MinIO.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentExportService {

    private final PackStorageService storageService;

    @Value("${minio.bucket.apo:apo-generees}")
    private String bucketApo;

    // ── APO FORMULAIRE (56 placeholders) ───────────────────────────────────────

    public String exportApo(ApoData apoData, String intituleOffre) {
        log.info("[Export] Génération APO DOCX — dossier {}", apoData.getDossierId());

        try (InputStream tplStream = new ClassPathResource("templates/APO-Formulaire-Etudes-Template.docx", this.getClass().getClassLoader()).getInputStream();
             XWPFDocument doc = new XWPFDocument(tplStream)) {

            replaceParagraphs(doc, apoData.getChamps());
            replaceTables(doc, apoData.getChamps());
            replaceHeadersFooters(doc, apoData.getChamps());

            byte[] bytes = toBytes(doc);
            String objectName = apoData.getDossierId() + "/APO_" + sanitize(intituleOffre) + ".docx";

            return storageService.uploadBytes(bucketApo, objectName, bytes,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        } catch (Exception e) {
            log.error("[Export] Erreur génération APO DOCX : {}", e.getMessage(), e);
            throw new RuntimeException("Génération APO DOCX échouée", e);
        }
    }

    // ── MÉTHODOLOGIE (5 sections) ──────────────────────────────────────────────

    public String exportMethodologie(MethodologieResponseDto methodo, ApoData apoData, DossierDto dossier) {
        log.info("[Export] Génération méthodologie DOCX");

        try (InputStream tplStream = new ClassPathResource("templates/Methodologie-Template.docx", this.getClass().getClassLoader()).getInputStream();
             XWPFDocument doc = new XWPFDocument(tplStream)) {

            // 🛠️ Sécurité Anti-NullPointerException : Utilisation de HashMap à la place de Map.of
            // La trame contient également les informations générales déjà assemblées dans ApoData.
            Map<String, ApoData.ChampApo> champsMethodo = apoData != null && apoData.getChamps() != null
                    ? new HashMap<>(apoData.getChamps())
                    : new HashMap<>();
            // Les métadonnées de couverture viennent directement du dossier : elles
            // ne dépendent donc jamais de la complétude de l'ApoData.
            champsMethodo.put("INTITULE_OFFRE", champ(valueOrDefault(dossier != null ? dossier.getIntituleOffre() : null)));
            champsMethodo.put("CLIENT",          champ(valueOrDefault(dossier != null ? dossier.getClient() : null)));
            champsMethodo.put("PAYS",            champ(valueOrDefault(dossier != null ? dossier.getPays() : null)));
            champsMethodo.put("BAILLEURS",       champ(valueOrDefault(dossier != null ? dossier.getBailleurs() : null)));
            champsMethodo.put("DT_LIM_SOUM",     champ(valueOrDefault(dossier != null ? dossier.getDtLimSoum() : null)));
            champsMethodo.put("SECTION_1_CONTEXTE",   champ(methodo != null ? methodo.getSection1_contexteEnjeux() : ""));
            champsMethodo.put("SECTION_2_APPROCHE",   champ(methodo != null ? methodo.getSection2_approchMethodologique() : ""));
            champsMethodo.put("SECTION_3_PLAN",       champ(methodo != null ? methodo.getSection3_planTravail() : ""));
            champsMethodo.put("SECTION_4_EQUIPE",     champ(methodo != null ? methodo.getSection4_compositionEquipe() : ""));
            champsMethodo.put("SECTION_5_RISQUES",    champ(methodo != null ? methodo.getSection5_gestionRisques() : ""));

            replaceParagraphs(doc, champsMethodo);
            replaceTables(doc, champsMethodo);
            replaceHeadersFooters(doc, champsMethodo);

            byte[] bytes = toBytes(doc);
            String objectName = "methodo/" + UUID.randomUUID() + "_Methodologie_"
                    + sanitize(dossier != null ? dossier.getIntituleOffre() : null) + ".docx";

            return storageService.uploadBytes("apo-generees", objectName, bytes,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        } catch (Exception e) {
            log.error("[Export] Erreur méthodologie DOCX : {}", e.getMessage(), e);
            throw new RuntimeException("Génération méthodologie échouée", e);
        }
    }

    // ── RAPPORT GÉNÉRAL RÉSULTAT ────────────────────────────────────────────────

    public String exportRapportResultat(ApoData apoData, PwinScore pwin, String intituleOffre) {
        log.info("[Export] Génération rapport général résultat DOCX");

        try (InputStream tplStream = new ClassPathResource("templates/Rapport-General-Template.docx", this.getClass().getClassLoader()).getInputStream();
             XWPFDocument doc = new XWPFDocument(tplStream)) {

            Map<String, ApoData.ChampApo> champsRapport = new LinkedHashMap<>(apoData.getChamps());
            if (pwin != null) {
                champsRapport.put("PWIN_GLOBAL",     champ(pwin.getScoreGlobal() != null ? String.format("%.1f%%", pwin.getScoreGlobal()) : "N/A"));
                champsRapport.put("DECISION_FINALE", champ(pwin.getDecisionAuto()));
                champsRapport.put("SCORE_A",         champ(pwin.getScoreA() != null ? String.format("%.0f%%", pwin.getScoreA() * 100) : "N/A"));
                champsRapport.put("SCORE_B",         champ(pwin.getScoreB() != null ? String.format("%.0f%%", pwin.getScoreB() * 100) : "N/A"));
                champsRapport.put("SCORE_C",         champ(pwin.getScoreC() != null ? String.format("%.0f%%", pwin.getScoreC() * 100) : "N/A"));
                champsRapport.put("SCORE_D",         champ(pwin.getScoreD() != null ? String.format("%.0f%%", pwin.getScoreD() * 100) : "N/A"));
                champsRapport.put("SCORE_E",         champ(pwin.getScoreE() != null ? String.format("%.0f%%", pwin.getScoreE() * 100) : "N/A"));
            } else {
                champsRapport.put("PWIN_GLOBAL",     champ("N/A"));
                champsRapport.put("DECISION_FINALE", champ("N/A"));
                champsRapport.put("SCORE_A",         champ("N/A"));
                champsRapport.put("SCORE_B",         champ("N/A"));
                champsRapport.put("SCORE_C",         champ("N/A"));
                champsRapport.put("SCORE_D",         champ("N/A"));
                champsRapport.put("SCORE_E",         champ("N/A"));
            }

            replaceParagraphs(doc, champsRapport);
            replaceTables(doc, champsRapport);

            byte[] bytes = toBytes(doc);
            String objectName = apoData.getDossierId() + "/Rapport_" + sanitize(intituleOffre) + ".docx";

            return storageService.uploadBytes("apo-generees", objectName, bytes,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        } catch (Exception e) {
            log.error("[Export] Erreur rapport résultat DOCX : {}", e.getMessage(), e);
            throw new RuntimeException("Génération rapport résultat échouée", e);
        }
    }

    // ── RAPPORT GÉNÉRAL (POUR L'ANALYSTE EN PHASE 3) ───────────────────────────
    public byte[] generateGeneralReportBytes(UUID dossierId, 
                                             DossierDto dossier, 
                                             MatchingResult matching, 
                                             tn.rihab.analysteservice.model.AnalyseDossier analyse, 
                                             tn.rihab.analysteservice.model.PwinScore pwin,
                                             tn.rihab.analysteservice.dto.ia.ApoTextsResponseDto texts) {
        log.info("[Export] Génération Rapport Général (byte[]) - dossier {}", dossierId);

        try (InputStream tplStream = new ClassPathResource("templates/Rapport-General-Template.docx", this.getClass().getClassLoader()).getInputStream();
             XWPFDocument doc = new XWPFDocument(tplStream)) {

            Map<String, ApoData.ChampApo> champsRapport = new LinkedHashMap<>();
            // Fill all fields expected by the Word template
            champsRapport.put("INTITULE_OFFRE", champ(dossier.getIntituleOffre() != null ? dossier.getIntituleOffre() : "Offre non spécifiée"));
            champsRapport.put("CLIENT",         champ(dossier.getClient()));
            champsRapport.put("PAYS",           champ(dossier.getPays()));
            champsRapport.put("BAILLEURS",      champ(dossier.getBailleurs() != null ? String.join(", ", dossier.getBailleurs()) : "N/A"));
            
            Double hm = dossier.getHommesMois();
            String budget = dossier.getBudgetGlobal();
            
            // Correction automatique : si l'IA a mis le budget dans Hommes-Mois
            if (hm != null && hm > 1000) {
                if (budget == null || budget.trim().isEmpty() || budget.equals("N/A")) {
                    budget = String.format(java.util.Locale.US, "%,.0f", hm).replace(',', ' ');
                }
                hm = null; // Réinitialiser car c'était une erreur de l'IA
            }
            
            // Ajouter la devise si elle manque
            if (budget != null && !budget.matches(".*[A-Za-z€$£].*") && !budget.equals("N/A")) {
                budget = budget.trim() + " €";
            }
            
            champsRapport.put("BUDGET_GLOBAL",  champ(budget != null ? budget : "N/A"));
            champsRapport.put("HOMMES_MOIS",    champ(hm != null ? String.format(java.util.Locale.US, "%.1f", hm).replace(".0", "") : "N/A"));
            champsRapport.put("DT_LIM_SOUM",    champ(dossier.getDtLimSoum() != null ? dossier.getDtLimSoum().toString() : "N/A"));
            
            if (pwin != null) {
                champsRapport.put("PWIN_GLOBAL",    champ(pwin.getScoreGlobal() != null ? String.format(java.util.Locale.US, "%.1f%%", pwin.getScoreGlobal()) : "N/A"));
                champsRapport.put("SCORE_A",        champ(pwin.getScoreA() != null ? String.format(java.util.Locale.US, "%.0f%%", pwin.getScoreA() * 100) : "N/A"));
                champsRapport.put("SCORE_B",        champ(pwin.getScoreB() != null ? String.format(java.util.Locale.US, "%.0f%%", pwin.getScoreB() * 100) : "N/A"));
                champsRapport.put("SCORE_C",        champ(pwin.getScoreC() != null ? String.format(java.util.Locale.US, "%.0f%%", pwin.getScoreC() * 100) : "N/A"));
                champsRapport.put("SCORE_D",        champ(pwin.getScoreD() != null ? String.format(java.util.Locale.US, "%.0f%%", pwin.getScoreD() * 100) : "N/A"));
                champsRapport.put("SCORE_E",        champ(pwin.getScoreE() != null ? String.format(java.util.Locale.US, "%.0f%%", pwin.getScoreE() * 100) : "N/A"));
                
                String finalDecision = pwin.getDecisionAuto() != null ? pwin.getDecisionAuto() : "N/A";
                if (matching != null && Boolean.FALSE.equals(matching.getCompatibleMethodologie())) {
                    finalDecision = "NO-GO (Non Compatible)";
                }
                champsRapport.put("DECISION_FINALE", champ(finalDecision));
            } else {
                champsRapport.put("PWIN_GLOBAL",    champ("Évaluation Matching Uniquement"));
                champsRapport.put("SCORE_A",        champ("N/A"));
                champsRapport.put("SCORE_B",        champ("N/A"));
                champsRapport.put("SCORE_C",        champ("N/A"));
                champsRapport.put("SCORE_D",        champ("N/A"));
                champsRapport.put("SCORE_E",        champ("N/A"));
                champsRapport.put("DECISION_FINALE", champ("NON COMPATIBLE (Absence P-Win)"));
            }
            
            // Matching results
            if (matching != null) {
                champsRapport.put("SECTEUR_AO",                  champ(matching.getSecteurAo() != null ? matching.getSecteurAo() : "N/A"));
                champsRapport.put("TAUX_COUVERTURE_COMPETENCES", champ(matching.getTauxCouvertureCompetences() != null ? String.format("%.0f%%", matching.getTauxCouvertureCompetences() * 100) : "N/A"));
                champsRapport.put("TAUX_COUVERTURE_EXPERTS",     champ(matching.getTauxCouvertureExperts() != null ? String.format("%.0f%%", matching.getTauxCouvertureExperts() * 100) : "N/A"));
                champsRapport.put("RELATION_CLIENT",             champ(matching.getRelationClientNiveau() != null ? String.format("%d/5", matching.getRelationClientNiveau()) : "N/A"));
                champsRapport.put("ALIGNEMENT_STRATEGIQUE",      champ(matching.getAlignementStrategique() != null ? matching.getAlignementStrategique() : "N/A"));
                champsRapport.put("GAP_REFS",                    champ(matching.getGapRefs() != null ? "Calculé dans le détail" : "N/A"));
                champsRapport.put("RECOMMANDATION_GO_NOGO",      champ(texts != null ? texts.getRecommandationGoNogo() : "NO-GO (Non Compatible)"));
                champsRapport.put("ARGUMENTAIRE_GO_NOGO",        champ(texts != null ? texts.getArgumentaireGoNogo() : (matching.getMotifIncompatibilite() != null ? matching.getMotifIncompatibilite() : "Le dossier n'a pas atteint les seuils minimaux requis.")));
            } else {
                champsRapport.put("SECTEUR_AO",                  champ("N/A"));
                champsRapport.put("TAUX_COUVERTURE_COMPETENCES", champ("N/A"));
                champsRapport.put("TAUX_COUVERTURE_EXPERTS",     champ("N/A"));
                champsRapport.put("RELATION_CLIENT",             champ("N/A"));
                champsRapport.put("ALIGNEMENT_STRATEGIQUE",      champ("N/A"));
                champsRapport.put("GAP_REFS",                    champ("N/A"));
                champsRapport.put("RECOMMANDATION_GO_NOGO",      champ("N/A"));
                champsRapport.put("ARGUMENTAIRE_GO_NOGO",        champ("N/A"));
            }

            // Phase 2 / Phase 4 Missing Context Fields (from AI texts)
            if (texts != null) {
                champsRapport.put("RESUME_CONTEXTE_OBJECTIFS", champ(texts.getResumeContexteObjectifs()));
                champsRapport.put("LISTE_CLARIFICATIONS",      champ(texts.getListeClarifications()));
                champsRapport.put("POINTS_CRITIQUES",          champ(texts.getPointsCritiques()));
            } else {
                champsRapport.put("RESUME_CONTEXTE_OBJECTIFS", champ("Non généré"));
                champsRapport.put("LISTE_CLARIFICATIONS",      champ("Non généré"));
                champsRapport.put("POINTS_CRITIQUES",          champ("Non généré"));
            }
            
            if (analyse != null && texts == null) {
                StringBuilder risques = new StringBuilder();
                formatRisque(risques, "Sécurité/Pays", analyse.getRisquePaysSecurite());
                formatRisque(risques, "Financier", analyse.getRisquesFinanciers());
                formatRisque(risques, "Pénalités", analyse.getPenalites());
                formatRisque(risques, "TDR", analyse.getExigencesTdrInacceptables());
                formatRisque(risques, "Garanties", analyse.getGarantiesAssurancesElevees());
                formatRisque(risques, "Taille/Dispersion", analyse.getTailleDispersion());
                formatRisque(risques, "Frais Divers", analyse.getFraisDiversEleves());
                formatRisque(risques, "Budget/HM", analyse.getBudgetFaibleHmLimites());
                formatRisque(risques, "Part. Locale", analyse.getParticipationLocaleExcessive());
                formatRisque(risques, "Fiscalité", analyse.getFiscaliteNonMaitrisee());
                
                champsRapport.put("POINTS_CRITIQUES", champ(risques.length() > 0 ? "Risques majeurs identifiés :\n" + risques.toString().trim() : "Aucun risque majeur identifié en Phase 2."));
            } else if (texts == null) {
                champsRapport.put("POINTS_CRITIQUES", champ("Analyse Phase 2 non disponible."));
            }

            replaceParagraphs(doc, champsRapport);
            replaceTables(doc, champsRapport);

            return toBytes(doc);
        } catch (Exception e) {
            log.error("[Export] Erreur génération rapport général DOCX en mémoire : {}", e.getMessage(), e);
            throw new RuntimeException("Génération rapport général échouée", e);
        }
    }

    public String exportGeneralReport(UUID dossierId, 
                                      DossierDto dossier, 
                                      MatchingResult matching, 
                                      tn.rihab.analysteservice.model.AnalyseDossier analyse, 
                                      tn.rihab.analysteservice.model.PwinScore pwin,
                                      tn.rihab.analysteservice.dto.ia.ApoTextsResponseDto texts) {
        log.info("[Export] Génération rapport général (Analyste) DOCX pour {}", dossierId);
        
        byte[] bytes = generateGeneralReportBytes(dossierId, dossier, matching, analyse, pwin, texts);
        String objectName = dossierId + "/Rapport_General_" + sanitize(dossier.getIntituleOffre()) + ".docx";

        return storageService.uploadBytes("apo-generees", objectName, bytes,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    private void formatRisque(StringBuilder sb, String label, String rawRisque) {
        if (rawRisque != null && (rawRisque.startsWith("Élevé") || rawRisque.startsWith("Rédhibitoire"))) {
            String[] parts = rawRisque.split("\\|\\|");
            if (parts.length >= 2) {
                sb.append("- ").append(label).append(" (").append(parts[0]).append(") : ").append(parts[1]).append("\n");
            } else {
                sb.append("- ").append(label).append(" : ").append(rawRisque).append("\n");
            }
        }
    }

    // ── RAPPORT NO-GO ──────────────────────────────────────────────────────────

    public String exportNoGoReport(NoGoReport rapport, DossierDto dossier, String analysteName, tn.rihab.analysteservice.model.PwinScore pwin) {
        try (InputStream tplStream = new ClassPathResource("templates/Rapport-NoGo-Template.docx", this.getClass().getClassLoader()).getInputStream();
             XWPFDocument doc = new XWPFDocument(tplStream)) {

            Map<String, ApoData.ChampApo> champs = new HashMap<>();
            champs.put("INTITULE_OFFRE",     champ(dossier.getIntituleOffre()));
            champs.put("CLIENT",             champ(dossier.getClient()));
            champs.put("PAYS",               champ(dossier.getPays() != null ? dossier.getPays() : "Non spécifié"));
            champs.put("DATE_GENERATION",    champ(java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
            champs.put("ANALYSTE",           champ(analysteName != null ? analysteName : "Système Expert IA"));
            
            champs.put("PWIN_GLOBAL",        champ(String.format("%.1f%%", rapport.getPwinScore())));
            
            // Format Motif Principaux
            String motifsFormattes = formatMotifsForDocx(rapport.getMotifsPrincipaux());
            champs.put("MOTIFS_PRINCIPAUX",  champ(motifsFormattes));
            champs.put("ANALYSE_NARRATIVE",  champ(rapport.getAnalyseNarrative()));
            
            // Forçage fields
            champs.put("FORCE_GO",           champ(pwin != null && Boolean.TRUE.equals(pwin.getForceGo()) ? "OUI" : ""));
            champs.put("FORCE_GO_JUSTIF",    champ(pwin != null && pwin.getForceGoJustif() != null ? pwin.getForceGoJustif() : ""));
            champs.put("FORCE_GO_TYPE",      champ(pwin != null && pwin.getForceGoType() != null ? pwin.getForceGoType() : ""));
            champs.put("FORCE_GO_BY",        champ(pwin != null && pwin.getForceGoBy() != null ? pwin.getForceGoBy() : ""));
            String forceGoAtStr = "";
            if (pwin != null && pwin.getForceGoAt() != null) {
                forceGoAtStr = pwin.getForceGoAt().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
            }
            champs.put("FORCE_GO_AT",        champ(forceGoAtStr));

            // Format Scoring Detail from DB JSON or just put a generic text since we don't have pwin object here easily
            // We can just omit SCORING_DETAIL if we don't have it, or put a placeholder
            champs.put("SCORING_DETAIL",     champ("Détail calculé via IA"));

            replaceParagraphs(doc, champs);
            replaceTables(doc, champs);

            byte[] bytes = toBytes(doc);
            String objectName = rapport.getDossierId() + "/NoGo_" + sanitize(dossier.getIntituleOffre()) + ".docx";

            return storageService.uploadBytes("nogo-reports", objectName, bytes,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        } catch (Exception e) {
            throw new RuntimeException("Génération NoGo DOCX échouée", e);
        }
    }

    // ── ALGORITHME DE REMPLACEMENT APACHE POI (CORRIGÉ) ───────────────────────

    private void replaceParagraphs(XWPFDocument doc, Map<String, ApoData.ChampApo> champs) {
        for (XWPFParagraph para : doc.getParagraphs()) {
            replaceInParagraph(para, champs);
        }
    }

    private void replaceTables(XWPFDocument doc, Map<String, ApoData.ChampApo> champs) {
        for (XWPFTable table : doc.getTables()) {
            replaceInTable(table, champs);
        }
    }

    private void replaceInTable(XWPFTable table, Map<String, ApoData.ChampApo> champs) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                cell.getParagraphs().forEach(para -> replaceInParagraph(para, champs));
                cell.getTables().forEach(nestedTable -> replaceInTable(nestedTable, champs));
            }
        }
    }

    private void replaceHeadersFooters(XWPFDocument doc, Map<String, ApoData.ChampApo> champs) {
        doc.getHeaderList().forEach(h -> h.getParagraphs().forEach(p -> replaceInParagraph(p, champs)));
        doc.getFooterList().forEach(f -> f.getParagraphs().forEach(p -> replaceInParagraph(p, champs)));
    }

    private void replaceInParagraph(XWPFParagraph para, Map<String, ApoData.ChampApo> champs) {
        if (para.getRuns() == null || para.getRuns().isEmpty()) return;

        // 1. Reconstitution du texte complet du paragraphe de manière propre
        StringBuilder fullText = new StringBuilder();
        for (XWPFRun r : para.getRuns()) {
            String val = r.getText(0);
            if (val != null) fullText.append(val);
        }
        String text = fullText.toString();

        // Si le paragraphe ne contient aucun placeholder, on ne touche à rien (on garde le style d'origine)
        if (!text.contains("[[")) return;

        // 2. Remplacer tous les jetons trouvés
        boolean modified = false;
        for (Map.Entry<String, ApoData.ChampApo> entry : champs.entrySet()) {
            String placeholder = "[[" + entry.getKey() + "]]";
            if (text.contains(placeholder)) {
                String valeur = entry.getValue().getValeur() != null ? entry.getValue().getValeur() : "";
                text = text.replace(placeholder, valeur);
                modified = true;
            }
        }

        // 3. Réinjection propre si modification (Sécurité pour le texte périphérique)
        if (modified) {
            // Écrase le premier run avec la totalité du texte traité
            XWPFRun firstRun = para.getRuns().get(0);
            String[] lines = text.split("\\R", -1);
            firstRun.setText(lines[0], 0);
            for (int i = 1; i < lines.length; i++) {
                firstRun.addBreak();
                firstRun.setText(lines[i]);
            }

            // 🛠️ Correction Pro : Suppression physique des runs résiduels pour éviter de polluer le document
            int totalRuns = para.getRuns().size();
            for (int i = totalRuns - 1; i > 0; i--) {
                para.removeRun(i);
            }
        }
    }

    // ── UTILITAIRES ────────────────────────────────────────────────────────────

    private byte[] toBytes(XWPFDocument doc) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            doc.write(baos);
            return baos.toByteArray();
        }
    }

    private ApoData.ChampApo champ(String valeur) {
        return ApoData.ChampApo.builder()
                .valeur(valeur != null ? valeur : "")
                .statut("AUTO")
                .source("system")
                .build();
    }

    private String valueOrDefault(Object value) {
        if (value == null || value.toString().isBlank()) return "Non précisé";
        return value.toString();
    }
    
    private String formatMotifsForDocx(String jsonMotifs) {
        if (jsonMotifs == null || !jsonMotifs.startsWith("[")) return jsonMotifs;
        try {
            StringBuilder text = new StringBuilder();
            String[] items = jsonMotifs.substring(1, jsonMotifs.length() - 1).split("\\},\\{");
            for (String item : items) {
                item = item.replace("{", "").replace("}", "").replace("\"", "");
                String axe = extractJsonValue(item, "axe");
                String champ = extractJsonValue(item, "champ");
                String niveau = extractJsonValue(item, "niveau");
                String score = extractJsonValue(item, "score");
                
                text.append("• Axe ").append(axe != null ? axe.replace("_", " ") : "");
                if (champ != null && !champ.isEmpty()) {
                    String readableChamp = champ.replace("_", " ").toLowerCase();
                    readableChamp = readableChamp.substring(0, 1).toUpperCase() + readableChamp.substring(1);
                    text.append(" (").append(readableChamp).append(")");
                }
                text.append(" : ");
                if (niveau != null && !niveau.isEmpty()) text.append(niveau);
                if (score != null && !score.isEmpty()) text.append(score);
                text.append("\n");
            }
            return text.toString().trim();
        } catch (Exception e) {
            return jsonMotifs; 
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

    private String sanitize(String name) {
        if (name == null) return "sans_titre";
        return name.replaceAll("[^a-zA-Z0-9À-ÿ_\\-]", "_");
    }
}
