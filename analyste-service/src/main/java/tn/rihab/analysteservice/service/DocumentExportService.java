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

        try (InputStream tplStream = new ClassPathResource("templates/APO-Formulaire Etudes-Template.docx").getInputStream();
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

    public String exportMethodologie(MethodologieResponseDto methodo, String intituleOffre) {
        log.info("[Export] Génération méthodologie DOCX");

        try (InputStream tplStream = new ClassPathResource("templates/Methodologie-Template1.docx").getInputStream();
             XWPFDocument doc = new XWPFDocument(tplStream)) {

            // 🛠️ Sécurité Anti-NullPointerException : Utilisation de HashMap à la place de Map.of
            Map<String, ApoData.ChampApo> champsMethodo = new HashMap<>();
            champsMethodo.put("SECTION_1_CONTEXTE",   champ(methodo != null ? methodo.getSection1_contexteEnjeux() : ""));
            champsMethodo.put("SECTION_2_APPROCHE",   champ(methodo != null ? methodo.getSection2_approchMethodologique() : ""));
            champsMethodo.put("SECTION_3_PLAN",       champ(methodo != null ? methodo.getSection3_planTravail() : ""));
            champsMethodo.put("SECTION_4_EQUIPE",     champ(methodo != null ? methodo.getSection4_compositionEquipe() : ""));
            champsMethodo.put("SECTION_5_RISQUES",    champ(methodo != null ? methodo.getSection5_gestionRisques() : ""));

            replaceParagraphs(doc, champsMethodo);
            replaceTables(doc, champsMethodo);

            byte[] bytes = toBytes(doc);
            String objectName = "methodo/" + UUID.randomUUID() + "_Methodologie_" + sanitize(intituleOffre) + ".docx";

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

        try (InputStream tplStream = new ClassPathResource("templates/Rapport-General-Template1.docx").getInputStream();
             XWPFDocument doc = new XWPFDocument(tplStream)) {

            Map<String, ApoData.ChampApo> champsRapport = new LinkedHashMap<>(apoData.getChamps());
            champsRapport.put("PWIN_GLOBAL",     champ(pwin.getScoreGlobal() != null ? String.format("%.1f%%", pwin.getScoreGlobal()) : "N/A"));
            champsRapport.put("DECISION_FINALE", champ(pwin.getDecisionAuto()));
            champsRapport.put("SCORE_A",         champ(pwin.getScoreA() != null ? String.format("%.0f%%", pwin.getScoreA() * 100) : "N/A"));
            champsRapport.put("SCORE_B",         champ(pwin.getScoreB() != null ? String.format("%.0f%%", pwin.getScoreB() * 100) : "N/A"));
            champsRapport.put("SCORE_C",         champ(pwin.getScoreC() != null ? String.format("%.0f%%", pwin.getScoreC() * 100) : "N/A"));
            champsRapport.put("SCORE_D",         champ(pwin.getScoreD() != null ? String.format("%.0f%%", pwin.getScoreD() * 100) : "N/A"));
            champsRapport.put("SCORE_E",         champ(pwin.getScoreE() != null ? String.format("%.0f%%", pwin.getScoreE() * 100) : "N/A"));

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

    // ── RAPPORT NO-GO ──────────────────────────────────────────────────────────

    public String exportNoGoReport(NoGoReport rapport, DossierDto dossier) {
        try (InputStream tplStream = new ClassPathResource("templates/Rapport-NoGo-Template1.docx").getInputStream();
             XWPFDocument doc = new XWPFDocument(tplStream)) {

            Map<String, ApoData.ChampApo> champs = new HashMap<>();
            champs.put("INTITULE_OFFRE",     champ(dossier.getIntituleOffre()));
            champs.put("CLIENT",             champ(dossier.getClient()));
            champs.put("PWIN_GLOBAL",        champ(String.format("%.1f%%", rapport.getPwinScore())));
            champs.put("ANALYSE_NARRATIVE",  champ(rapport.getAnalyseNarrative()));
            champs.put("MOTIFS_PRINCIPAUX",  champ(rapport.getMotifsPrincipaux()));

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
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph para : cell.getParagraphs()) {
                        replaceInParagraph(para, champs);
                    }
                }
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
            para.getRuns().get(0).setText(text, 0);

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

    private String sanitize(String name) {
        if (name == null) return "sans_titre";
        return name.replaceAll("[^a-zA-Z0-9À-ÿ_\\-]", "_");
    }
}