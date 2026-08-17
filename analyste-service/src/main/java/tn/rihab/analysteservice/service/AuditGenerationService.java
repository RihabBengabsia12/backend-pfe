package tn.rihab.analysteservice.service;

import tn.rihab.analysteservice.client.*;
import tn.rihab.analysteservice.dto.AuditEntryDto;
import tn.rihab.analysteservice.dto.DossierDto;
import tn.rihab.analysteservice.dto.ia.*;
import tn.rihab.analysteservice.messaging.AnalysteEventPublisher;
import tn.rihab.analysteservice.model.*;
import tn.rihab.analysteservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Génère le rapport d'audit complet (Phase 6).
 *
 * Déclenché par l'événement DOSSIER_SUBMITTED (project-service).
 *
 * Contenu du rapport d'audit :
 *   - Timeline chronologique de toutes les actions du cycle de vie
 *   - Score P-Win avec décomposition par axe
 *   - Décision finale et validateurs
 *   - Traçabilité des corrections humaines
 *   - Forçages Go éventuels (avec justification)
 *   - Analyse narrative générée par Claude (points d'amélioration)
 *   - Résultat final (gagné/perdu — saisi ultérieurement)
 *
 * Publie AUDIT_GENERATED → project-service passe en ARCHIVED.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditGenerationService {

    private final ProjectServiceClient    projectClient;
    private final IaServiceClient         iaClient;
    private final PackStorageService      storageService;
    private final AnalysteEventPublisher  publisher;
    private final PwinScoreRepository     pwinRepo;
    private final AnalyseDossierRepository analyseRepo;
    private final tn.rihab.analysteservice.repository.IaAuditLogRepository auditLogRepo;

    /**
     * Génère le rapport d'audit DOCX et le stocke sur MinIO.
     * Publie l'événement AUDIT_GENERATED vers project-service.
     *
     * @param dossierId ID du dossier à auditer
     */
    public void generateAuditReport(UUID dossierId) {
        log.info("[Audit] Génération rapport d'audit — dossier {}", dossierId);

        // 1. Récupérer toutes les données nécessaires
        DossierDto dossier         = projectClient.getDossier(dossierId);
        List<AuditEntryDto> trail  = projectClient.getAuditHistory(dossierId);
        List<Map<String, Object>> validations = projectClient.getValidationStatus(dossierId);
        PwinScore pwin             = pwinRepo.findByDossierId(dossierId).orElse(null);
        FinalDecision finalDecision = resolveFinalDecision(validations);

        // 2. Construire le contexte pour Claude
        AuditContextDto context = AuditContextDto.builder()
                .dossierId(dossierId)
                .intituleOffre(dossier.getIntituleOffre())
                .client(dossier.getClient())
                .pwinScore(pwin != null ? pwin.getScoreGlobal() : null)
                .decisionFinale(pwin != null ? pwin.getDecisionAuto() : "INCONNU")
                .auditTrail(trail)
                .nbChampsCorrigesHumain((int) trail.stream()
                        .filter(e -> "FIELD_CORRECTED".equals(e.getAction())).count())
                .aEuForceGo(trail.stream()
                        .anyMatch(e -> "FORCE_GO".equals(e.getAction())))
                .build();

        // 3. Générer l'analyse narrative via Claude
        AuditReportResponseDto aiReport = null;
        try {
            aiReport = iaClient.generateAuditReport(context);
            saveAudit(dossierId, "GENERATION_AUDIT_REPORT", aiReport.getStats());
        } catch (Exception e) {
            log.warn("[Audit] Génération Claude échouée (non bloquant) : {}", e.getMessage());
        }

        // 4. Générer le DOCX du rapport d'audit
        String auditDocxPath = generateAuditDocx(
                dossierId, dossier, trail, pwin, aiReport, finalDecision, validations);

        // 5. Publier → project-service stocke le chemin et passe en ARCHIVED
        publisher.publishAuditGenerated(dossierId, auditDocxPath);

        log.info("[Audit] Rapport d'audit généré : {}", auditDocxPath);
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

    // ── Génération DOCX du rapport d'audit ─────────────────────────────────────

    private String generateAuditDocx(UUID dossierId,
                                     DossierDto dossier,
                                     List<AuditEntryDto> trail,
                                     PwinScore pwin,
                                     AuditReportResponseDto aiReport,
                                     FinalDecision finalDecision,
                                     List<Map<String, Object>> validations) {
        XWPFDocument doc;
        boolean templateLoaded = false;
        try {
            InputStream tplStream;
            try {
                tplStream = new ClassPathResource(
                        "templates/Template_Rapport_Audit_ProjectIQ.docx").getInputStream();
            } catch (Exception ignored) {
                // Copie explicite montée dans l'image Docker pour préserver la vraie trame.
                tplStream = Files.newInputStream(Path.of("/app/templates/Template_Rapport_Audit_ProjectIQ.docx"));
            }
            doc = new XWPFDocument(tplStream);
            tplStream.close();
            templateLoaded = true;
        } catch (Exception templateError) {
            // Un rapport d'audit ne doit jamais laisser un dossier bloqué en statut AUDIT.
            // Le document structuré ci-dessous garantit la traçabilité même si le modèle
            // n'est pas accessible dans une image Docker ancienne ou incomplète.
            log.warn("[Audit] Modèle DOCX indisponible, génération du rapport structuré de secours : {}",
                    templateError.getMessage());
            doc = new XWPFDocument();
        }

        final XWPFDocument auditDocument = doc;
        try (auditDocument) {

            // Construire la map de remplacement pour le template
            Map<String, String> valeurs = buildAuditValues(dossier, trail, pwin, aiReport, finalDecision);

            if (templateLoaded) {
                // 1. Injecter les lignes d'audit (Audit Trail)
                injectTimelineRows(doc, trail);
                injectValidationRows(doc, validations);

                // 2. Remplacer les placeholders partout
                replacePlaceholdersInDoc(doc, valeurs);
            } else {
                buildFallbackAuditDocument(doc, dossier, trail, pwin, aiReport, finalDecision);
            }

            // Convertir en bytes et uploader
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.write(baos);
            byte[] bytes = baos.toByteArray();

            String objectName = dossierId + "/Audit_"
                    + sanitize(dossier.getIntituleOffre()) + ".docx";

            return storageService.uploadBytes(
                    "rapports-audit", objectName, bytes,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        } catch (Exception e) {
            log.error("[Audit] Erreur génération DOCX : {}", e.getMessage(), e);
            throw new RuntimeException("Génération rapport audit échouée", e);
        }
    }

    /** Rapport lisible de secours : utilisé uniquement si le fichier modèle est indisponible. */
    private void buildFallbackAuditDocument(XWPFDocument doc,
                                            DossierDto dossier,
                                            List<AuditEntryDto> trail,
                                            PwinScore pwin,
                                            AuditReportResponseDto aiReport,
                                            FinalDecision finalDecision) {
        org.apache.poi.xwpf.usermodel.XWPFParagraph title = doc.createParagraph();
        title.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
        org.apache.poi.xwpf.usermodel.XWPFRun titleRun = title.createRun();
        titleRun.setBold(true);
        titleRun.setFontSize(16);
        titleRun.setText("RAPPORT D'AUDIT — PROJECTIQ");

        addFallbackParagraph(doc, "Dossier : " + safe(dossier.getIntituleOffre()), true);
        addFallbackParagraph(doc, "Client : " + safe(dossier.getClient()));
        addFallbackParagraph(doc, "Pays : " + safe(dossier.getPays()));
        addFallbackParagraph(doc, "Date de génération : " + java.time.LocalDate.now());

        addFallbackHeading(doc, "Synthèse de la décision");
        addFallbackParagraph(doc, "Décision finale : " + finalDecision.label(), true);
        addFallbackParagraph(doc, finalDecision.details());
        addFallbackParagraph(doc, buildScoringSection(pwin));

        addFallbackHeading(doc, "Analyse narrative");
        addFallbackParagraph(doc, aiReport != null ? safe(aiReport.getNarratif()) : "Non disponible.");

        addFallbackHeading(doc, "Points d'amélioration");
        addFallbackParagraph(doc, aiReport != null ? safe(aiReport.getPointsAmelioration()) : "Non disponible.");

        addFallbackHeading(doc, "Traçabilité des actions");
        org.apache.poi.xwpf.usermodel.XWPFTable table = doc.createTable();
        while (table.getRow(0).getTableCells().size() < 4) table.getRow(0).createCell();
        String[] headers = {"Date", "Acteur", "Action", "Détail"};
        for (int i = 0; i < headers.length; i++) setCellText(table.getRow(0).getCell(i), headers[i], false);
        if (trail == null || trail.isEmpty()) {
            org.apache.poi.xwpf.usermodel.XWPFTableRow row = table.createRow();
            setCellText(row.getCell(0), "Aucune action enregistrée.", false);
        } else {
            for (AuditEntryDto entry : trail) {
                org.apache.poi.xwpf.usermodel.XWPFTableRow row = table.createRow();
                setCellText(row.getCell(0), entry.getTimestamp() == null ? "" : entry.getTimestamp().toString(), false);
                setCellText(row.getCell(1), safe(entry.getActeur()), false);
                setCellText(row.getCell(2), safe(entry.getAction()), false);
                setCellText(row.getCell(3), safe(entry.getDetail()), false);
            }
        }
    }

    private void addFallbackHeading(XWPFDocument doc, String text) {
        org.apache.poi.xwpf.usermodel.XWPFParagraph paragraph = doc.createParagraph();
        org.apache.poi.xwpf.usermodel.XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontSize(12);
        run.setText(text);
    }

    private void addFallbackParagraph(XWPFDocument doc, String text) {
        addFallbackParagraph(doc, text, false);
    }

    private void addFallbackParagraph(XWPFDocument doc, String text, boolean bold) {
        org.apache.poi.xwpf.usermodel.XWPFParagraph paragraph = doc.createParagraph();
        org.apache.poi.xwpf.usermodel.XWPFRun run = paragraph.createRun();
        run.setBold(bold);
        run.setFontSize(10);
        run.setText(safe(text));
    }

    // ── Construction des valeurs de remplacement ────────────────────────────────

    private Map<String, String> buildAuditValues(DossierDto dossier,
                                                 List<AuditEntryDto> trail,
                                                 PwinScore pwin,
                                                 AuditReportResponseDto aiReport,
                                                 FinalDecision finalDecision) {

        String scoringSection = buildScoringSection(pwin);
        String correctionsSection = buildCorrectionsSection(trail);
        // ia-service historique renvoie son texte sous "rapport". Les nouvelles
        // versions peuvent renvoyer "narratif" et "pointsAmelioration" : on
        // accepte les deux formats pour que la template ne reste jamais vide.
        String narratif = firstNonBlank(
                aiReport != null ? aiReport.getNarratif() : null,
                aiReport != null ? aiReport.getRapport() : null,
                buildAutomaticNarrative(dossier, pwin, finalDecision));
        String ameliorations = firstNonBlank(
                aiReport != null ? aiReport.getPointsAmelioration() : null,
                buildAutomaticRecommendations(pwin, trail));

        Map<String, String> values = Map.ofEntries(
                Map.entry("[[INTITULE_OFFRE]]",         safe(dossier.getIntituleOffre())),
                Map.entry("[[CLIENT]]",                  safe(dossier.getClient())),
                Map.entry("[[PAYS]]",                    safe(dossier.getPays())),
                Map.entry("[[BAILLEURS]]",               safe(dossier.getBailleurs())),
                Map.entry("[[BUDGET_GLOBAL]]",           safe(dossier.getBudgetGlobal())),
                Map.entry("[[DT_LIM_SOUM]]",             safe(fmt(dossier.getDtLimSoum()))),
                Map.entry("[[PWIN_SCORE]]",              pwin != null && pwin.getScoreGlobal() != null ? String.format("%.1f%%", pwin.getScoreGlobal()) : "N/A"),
                Map.entry("[[DECISION_FINALE]]",         finalDecision.label()),
                Map.entry("[[VALIDATION_DETAILS]]",      finalDecision.details()),
                Map.entry("[[FINAL_DECISION_SOURCE]]",   finalDecision.source()),
                Map.entry("[[FINAL_DECISION_AT]]",       finalDecision.dateTime()),
                Map.entry("[[PACK_DOCUMENTS]]",          packDocuments(dossier)),
                Map.entry("[[VALIDATION_SENT_AT]]",      findAuditTimestamp(trail, "VALIDATION_SENT")),
                Map.entry("[[ARCHIVED_AT]]",             findAuditTimestamp(trail, "ARCHIVED")),
                Map.entry("[[FINAL_STATUS]]",            "ARCHIVED"),
                Map.entry("[[RISQUE_REDHIBITOIRE]]",     pwin != null && Boolean.TRUE.equals(pwin.getRisqueRedhibitoire())
                        ? "OUI — " + pwin.getRisqueRedhibitoireChamp() : "Non"),
                Map.entry("[[FORCE_GO]]",                pwin != null && Boolean.TRUE.equals(pwin.getForceGo())
                        ? "OUI — " + safe(pwin.getForceGoJustif()) : "Non"),
                Map.entry("[[SCORING_DETAIL]]",          "Recommandation automatique P-Win : "
                        + (pwin != null ? safe(pwin.getDecisionAuto()) : "N/A") + "\n"
                        + finalDecision.details() + "\n\n" + scoringSection),
                Map.entry("[[CORRECTIONS_HUMAINES]]",    correctionsSection),
                Map.entry("[[NB_CORRECTIONS]]",          String.valueOf(trail.stream()
                        .filter(e -> "FIELD_CORRECTED".equals(e.getAction())).count())),
                Map.entry("[[ANALYSE_NARRATIVE]]",       narratif),
                Map.entry("[[POINTS_AMELIORATION]]",     ameliorations),
                Map.entry("[[DATE_GENERATION]]",         java.time.LocalDate.now().toString())
        );
        // Les données intermédiaires restent masquées ; seul le contenu écrit
        // dans le rapport final est décapsulé.
        Map<String, String> exportValues = new LinkedHashMap<>();
        values.forEach((key, value) -> exportValues.put(key, decapsulate(dossier.getId(), value)));
        return exportValues;
    }

    private String decapsulate(UUID dossierId, String text) {
        if (text == null || text.isBlank()) return text;
        try {
            return projectClient.decapsulate(dossierId, text);
        } catch (Exception e) {
            log.error("[Audit] Erreur de décapsulation DLP pour le dossier {}", dossierId, e);
            return text;
        }
    }

    // ── Sections textuelles du rapport ─────────────────────────────────────────

    private void injectTimelineRows(XWPFDocument doc, List<AuditEntryDto> trail) {
        org.apache.poi.xwpf.usermodel.XWPFTable targetTable = null;
        int templateRowIdx = -1;

        for (org.apache.poi.xwpf.usermodel.XWPFTable table : doc.getTables()) {
            for (int i = 0; i < table.getRows().size(); i++) {
                org.apache.poi.xwpf.usermodel.XWPFTableRow row = table.getRow(i);
                for (org.apache.poi.xwpf.usermodel.XWPFTableCell cell : row.getTableCells()) {
                    if (cell.getText() != null && cell.getText().contains("[[TIMELINE_ACTIONS]]")) {
                        targetTable = table;
                        templateRowIdx = i;
                        break;
                    }
                }
                if (targetTable != null) break;
            }
            if (targetTable != null) break;
        }

        if (targetTable == null || templateRowIdx == -1) return;

        if (trail != null && !trail.isEmpty()) {
            for (AuditEntryDto entry : trail) {
                org.apache.poi.xwpf.usermodel.XWPFTableRow newRow = targetTable.createRow();
                while (newRow.getTableCells().size() < 5) newRow.createCell();

                String time = entry.getTimestamp() != null ? entry.getTimestamp().toString().substring(0, 16).replace("T", " ") : "";
                String acteur = safe(entry.getActeur());
                String action = safe(entry.getAction());
                String status = safe(entry.getStatusAvant()) + " → " + safe(entry.getStatusApres());
                String detail = safe(entry.getDetail());

                setCellText(newRow.getCell(0), time, true);
                setCellText(newRow.getCell(1), acteur, false);
                setCellText(newRow.getCell(2), action, false);
                setCellText(newRow.getCell(3), status, true);
                setCellText(newRow.getCell(4), detail, false);
            }
        } else {
            org.apache.poi.xwpf.usermodel.XWPFTableRow newRow = targetTable.createRow();
            while (newRow.getTableCells().size() < 5) newRow.createCell();
            setCellText(newRow.getCell(0), "Aucune action enregistrée.", false);
        }

        targetTable.removeRow(templateRowIdx);
    }

    private void setCellText(org.apache.poi.xwpf.usermodel.XWPFTableCell cell, String text, boolean mono) {
        if (cell.getParagraphs().isEmpty()) cell.addParagraph();
        org.apache.poi.xwpf.usermodel.XWPFParagraph p = cell.getParagraphs().get(0);
        if (p.getRuns().isEmpty()) p.createRun();
        org.apache.poi.xwpf.usermodel.XWPFRun run = p.getRuns().get(0);
        run.setText(text, 0);
        run.setFontFamily(mono ? "Courier New" : "Calibri");
        run.setFontSize(8);
    }

    private String buildScoringSection(PwinScore pwin) {
        if (pwin == null) return "Scoring non disponible.";
        return String.format(
                "Score Global : %.1f%%  |  Décision : %s\n" +
                        "  Axe A (Faisabilité 25%%) : %.0f%%\n" +
                        "  Axe B (Rentabilité 25%%) : %.0f%%\n" +
                        "  Axe C (Risques    25%%) : %.0f%%  %s\n" +
                        "  Axe D (Concurrence15%%) : %.0f%%\n" +
                        "  Axe E (Conformité 10%%) : %.0f%%",
                pwin.getScoreGlobal(), pwin.getDecisionAuto(),
                safe100(pwin.getScoreA()), safe100(pwin.getScoreB()),
                safe100(pwin.getScoreC()),
                Boolean.TRUE.equals(pwin.getRisqueRedhibitoire())
                        ? "⚠ RÉDHIBITOIRE : " + pwin.getRisqueRedhibitoireChamp() : "",
                safe100(pwin.getScoreD()), safe100(pwin.getScoreE()));
    }

    private String buildCorrectionsSection(List<AuditEntryDto> trail) {
        StringBuilder sb = new StringBuilder();
        trail.stream()
                .filter(e -> "FIELD_CORRECTED".equals(e.getAction()))
                .forEach(e -> sb.append(String.format("  - %s par %s : %s\n",
                        safe(e.getAction()), safe(e.getActeur()), safe(e.getDetail()))));
        return sb.length() > 0 ? sb.toString() : "Aucune correction manuelle.";
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void replacePlaceholdersInDoc(XWPFDocument doc, Map<String, String> valeurs) {
        doc.getParagraphs().forEach(p -> replaceInParagraph(p, valeurs));
        doc.getTables().forEach(t -> replaceInTable(t, valeurs));

        doc.getHeaderList().forEach(h -> {
            h.getParagraphs().forEach(p -> replaceInParagraph(p, valeurs));
            h.getTables().forEach(t -> replaceInTable(t, valeurs));
        });
        doc.getFooterList().forEach(f -> {
            f.getParagraphs().forEach(p -> replaceInParagraph(p, valeurs));
            f.getTables().forEach(t -> replaceInTable(t, valeurs));
        });
    }

    private void replaceInTable(org.apache.poi.xwpf.usermodel.XWPFTable table, Map<String, String> valeurs) {
        for (org.apache.poi.xwpf.usermodel.XWPFTableRow row : table.getRows()) {
            for (org.apache.poi.xwpf.usermodel.XWPFTableCell cell : row.getTableCells()) {
                cell.getParagraphs().forEach(p -> replaceInParagraph(p, valeurs));
            }
        }
    }

    private void replaceInParagraph(org.apache.poi.xwpf.usermodel.XWPFParagraph para,
                                    Map<String, String> valeurs) {
        if (para.getRuns().isEmpty()) return;
        StringBuilder full = new StringBuilder();
        para.getRuns().forEach(r -> full.append(r.getText(0) != null ? r.getText(0) : ""));
        String text = full.toString();
        if (!text.contains("[[")) return;

        for (Map.Entry<String, String> e : valeurs.entrySet()) {
            text = text.replace(e.getKey(), safe(e.getValue()));
        }

        // Garder le style du premier run
        for (int i = para.getRuns().size() - 1; i > 0; i--) {
            para.removeRun(i);
        }
        org.apache.poi.xwpf.usermodel.XWPFRun run = para.getRuns().get(0);
        run.setText("", 0); // Vider le texte initial
        
        // Supporter les sauts de ligne
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            run.setText(lines[i]);
            if (i < lines.length - 1) run.addBreak();
        }
    }

    private FinalDecision resolveFinalDecision(List<Map<String, Object>> validations) {
        if (validations == null || validations.isEmpty()) {
            return new FinalDecision("Decision manager non disponible", "Aucune decision de manager n'a ete retrouvee.", "Non disponible", "Non disponible");
        }
        List<Map<String, Object>> rejected = validations.stream()
                .filter(v -> "REJECTED".equals(v.get("status")) || "APPROVE_NOGO".equals(v.get("status")))
                .toList();
        List<Map<String, Object>> approved = validations.stream()
                .filter(v -> "APPROVED".equals(v.get("status"))).toList();
        boolean allApproved = approved.size() == validations.size();
        String managers = validations.stream().map(v -> {
            String name = String.valueOf(v.getOrDefault("nom", ""));
            String role = String.valueOf(v.getOrDefault("role", ""));
            String source = String.valueOf(v.getOrDefault("decisionSource", "PLATFORM"));
            Object date = v.get("actionAt");
            return (name.isBlank() ? role : name + " (" + role + ")") + " - " + source
                    + (date != null ? " - " + date : "");
        }).collect(java.util.stream.Collectors.joining("\n"));
        String sources = validations.stream().map(v -> String.valueOf(v.getOrDefault("decisionSource", "PLATFORM")))
                .distinct().collect(java.util.stream.Collectors.joining(", "));
        String finalAt = validations.stream().map(v -> v.get("actionAt")).filter(java.util.Objects::nonNull)
                .map(Object::toString).max(String::compareTo).orElse("Non disponible");
        if (!rejected.isEmpty()) return new FinalDecision("NO-GO confirme", "Decision finale prise par :\n" + managers, sources, finalAt);
        if (allApproved) return new FinalDecision("GO approuve par tous les decideurs", "Decision finale prise par :\n" + managers, sources, finalAt);
        return new FinalDecision("Decision en attente", "Etat des validations :\n" + managers, sources, finalAt);
    }

    /** Remplit le tableau final des validations managers de la template. */
    private void injectValidationRows(XWPFDocument doc, List<Map<String, Object>> validations) {
        org.apache.poi.xwpf.usermodel.XWPFTable target = null;
        int placeholderRow = -1;
        for (org.apache.poi.xwpf.usermodel.XWPFTable table : doc.getTables()) {
            for (int i = 0; i < table.getRows().size(); i++) {
                if (table.getRow(i).getTableCells().stream().anyMatch(c -> c.getText().contains("[[VALIDATION_ROWS]]"))) {
                    target = table;
                    placeholderRow = i;
                    break;
                }
            }
            if (target != null) break;
        }
        if (target == null) return;
        if (validations != null) {
            for (Map<String, Object> validation : validations) {
                org.apache.poi.xwpf.usermodel.XWPFTableRow row = target.createRow();
                while (row.getTableCells().size() < 6) row.createCell();
                String status = String.valueOf(validation.getOrDefault("status", ""));
                String decision = "APPROVED".equals(status) ? "GO approuve" :
                        ("REJECTED".equals(status) || "APPROVE_NOGO".equals(status) ? "NO-GO rejete" : "En attente");
                setCellText(row.getCell(0), String.valueOf(validation.getOrDefault("nom", "")), false);
                setCellText(row.getCell(1), String.valueOf(validation.getOrDefault("role", "")), false);
                setCellText(row.getCell(2), decision, false);
                setCellText(row.getCell(3), String.valueOf(validation.getOrDefault("decisionSource", "PLATFORM")), false);
                Object at = validation.get("actionAt");
                setCellText(row.getCell(4), at == null ? "-" : at.toString().replace("T", " "), false);
                setCellText(row.getCell(5), String.valueOf(validation.getOrDefault("commentaire", "")), false);
            }
        }
        target.removeRow(placeholderRow);
    }

    private String packDocuments(DossierDto dossier) {
        java.util.List<String> documents = new java.util.ArrayList<>();
        if (dossier.getRapportPath() != null) documents.add("Rapport general");
        if (dossier.getApoDocxPath() != null) documents.add("Rapport APO");
        if (dossier.getMethodoDocxPath() != null) documents.add("Methodologie");
        if (dossier.getPackZipPath() != null) documents.add("Pack ZIP de soumission");
        return documents.isEmpty() ? "Aucun document de pack enregistre" : String.join(" | ", documents);
    }

    private String buildAutomaticNarrative(DossierDto dossier, PwinScore pwin, FinalDecision decision) {
        String score = pwin != null && pwin.getScoreGlobal() != null
                ? String.format("%.1f%%", pwin.getScoreGlobal()) : "non disponible";
        return "Le dossier \"" + safe(dossier.getIntituleOffre()) + "\" a suivi le cycle ProjectIQ, "
                + "depuis l'analyse et le scoring jusqu'a la generation du pack et sa validation. "
                + "Le P-Win final est de " + score + ". La decision enregistree est : "
                + decision.label() + ".";
    }

    private String buildAutomaticRecommendations(PwinScore pwin, List<AuditEntryDto> trail) {
        java.util.List<String> points = new java.util.ArrayList<>();
        if (pwin != null && pwin.getScoreGlobal() != null && pwin.getScoreGlobal() < 70) {
            points.add("Renforcer les axes les moins performants du scoring avant la prochaine consultation comparable.");
        }
        long corrections = trail == null ? 0 : trail.stream()
                .filter(e -> "FIELD_CORRECTED".equals(e.getAction())).count();
        if (corrections == 0) points.add("Maintenir une revue humaine formelle des champs extraits avant validation finale.");
        points.add("Conserver la trace des validations et des documents du pack pour les consultations futures.");
        return String.join("\n", points);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "Non disponible.";
    }

    private String findAuditTimestamp(List<AuditEntryDto> trail, String action) {
        if (trail == null) return "Non disponible";
        return trail.stream().filter(e -> action.equals(e.getAction())).map(AuditEntryDto::getTimestamp)
                .filter(java.util.Objects::nonNull).max(java.time.LocalDateTime::compareTo)
                .map(Object::toString).orElse("Non disponible");
    }

    private record FinalDecision(String label, String details, String source, String dateTime) {}

    private String safe(String v)    { return v != null ? v : ""; }
    private String fmt(Object v)     { return v != null ? v.toString() : ""; }
    private double safe100(Double v) { return v != null ? v * 100 : 0; }

    private String sanitize(String name) {
        if (name == null) return "audit";
        return name.replaceAll("[^a-zA-Z0-9À-ÿ_\\-]", "_")
                .substring(0, Math.min(name.length(), 40));
    }
}
