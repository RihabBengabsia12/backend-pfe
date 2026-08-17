package tn.rihab.projectservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tn.rihab.projectservice.model.entity.Dossier;
import tn.rihab.projectservice.model.entity.ValidationToken;

import jakarta.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.base-url:http://localhost:4200}")
    private String baseUrl;

    private String fromEmail = "ProjectIQ <noreply@st2i.com.tn>";

    // ── Email de validation ────────────────────────────────────────────────────

    /**
     * Envoie le lien de validation au signataire.
     *
     * @param token   Token généré pour ce validateur
     * @param dossier Dossier à valider
     */
    @Async
    public void sendValidationEmail(ValidationToken token, Dossier dossier, tn.rihab.projectservice.dto.MatchingResultDto matchingResult) {
        String lienApprobation = baseUrl + "/validate/" + token.getToken() + "?action=APPROVED";
        String lienRejet       = baseUrl + "/validate/" + token.getToken() + "?action=REJECTED";

        String subject = "ProjectIQ — Action requise : GO/NO-GO — " + truncate(dossier.getIntituleOffre(), 60);

        String body = """
            <html><body style="margin:0; padding:0; background:#ffffff; font-family:Roboto,Arial,Helvetica,sans-serif; color:#202124; font-size:14px; line-height:1.5;">
              <div style="max-width:650px; margin:0 auto; padding:20px 24px;">
              <h2 style="font-weight:400; font-size:20px; line-height:28px; border-bottom:1px solid #dadce0; padding:0 0 14px; margin:0 0 22px;">Validation requise : GO / NO-GO</h2>
              <p style="margin:0 0 16px;">Bonjour Madame, Monsieur,</p>
              <p style="margin:0 0 22px; color:#3c4043;">L'équipe d'analyse a finalisé l'étude du dossier référencé ci-dessous. Veuillez consulter la synthèse avant de rendre votre décision.</p>
              
              <table role="presentation" style="width:100%%; border-collapse:collapse; margin:0 0 22px; font-size:14px;">
                <tr><td style="padding: 8px 0; color: #555; width: 30%%; border-bottom: 1px solid #eee;">Intitulé</td>
                    <td style="padding: 8px 0; font-weight: 500; border-bottom: 1px solid #eee;">%s</td></tr>
                <tr><td style="padding: 8px 0; color: #555; border-bottom: 1px solid #eee;">Client</td>
                    <td style="padding: 8px 0; border-bottom: 1px solid #eee;">%s</td></tr>
                <tr><td style="padding: 8px 0; color: #555; border-bottom: 1px solid #eee;">Pays</td>
                    <td style="padding: 8px 0; border-bottom: 1px solid #eee;">%s</td></tr>
                <tr><td style="padding: 8px 0; color: #555; border-bottom: 1px solid #eee;">Budget</td>
                    <td style="padding: 8px 0; border-bottom: 1px solid #eee;">%s</td></tr>
                <tr><td style="padding: 8px 0; color: #555; border-bottom: 1px solid #eee;">Score P-Win</td>
                    <td style="padding: 8px 0; font-weight: bold; border-bottom: 1px solid #eee;">%s%%</td></tr>
                <tr><td style="padding: 8px 0; color: #555; border-bottom: 1px solid #eee;">Compétences</td>
                    <td style="padding: 8px 0; border-bottom: 1px solid #eee;">%s</td></tr>
                <tr><td style="padding: 8px 0; color: #555; border-bottom: 1px solid #eee;">Références</td>
                    <td style="padding: 8px 0; border-bottom: 1px solid #eee;">%s</td></tr>
                <tr><td style="padding: 8px 0; color: #555; border-bottom: 1px solid #eee;">Experts</td>
                    <td style="padding: 8px 0; border-bottom: 1px solid #eee;">%s</td></tr>
                <tr><td style="padding: 8px 0; color: #555; border-bottom: 1px solid #eee;">Alignement</td>
                    <td style="padding: 8px 0; border-bottom: 1px solid #eee;">%s</td></tr>
                <tr><td style="padding: 8px 0; color: #555; border-bottom: 1px solid #eee;">Décision IA</td>
                    <td style="padding: 8px 0; border-bottom: 1px solid #eee;">%s</td></tr>
                <tr><td style="padding: 8px 0; color: #555; border-bottom: 1px solid #eee;">Date limite</td>
                    <td style="padding: 8px 0; border-bottom: 1px solid #eee;">%s</td></tr>
              </table>
              
              <p style="margin:0 0 10px;">Le pack décisionnel complet est prêt à être consulté :</p>
              
              <ul style="list-style-type:none; padding:0; margin:0 0 22px;">
                <li style="margin-bottom:7px;">• <a href="%s/api/validation/token/%s/document/rapport" target="_blank" style="color:#1967d2; text-decoration:none; font-weight:500;">Rapport général (DOCX)</a></li>
                <li style="margin-bottom:7px;">• <a href="%s/api/validation/token/%s/document/apo" target="_blank" style="color:#1967d2; text-decoration:none; font-weight:500;">Rapport APO (DOCX)</a></li>
                <li style="margin-bottom:7px;">• <a href="%s/api/validation/token/%s/document/pack" target="_blank" style="color:#1967d2; text-decoration:none; font-weight:500;">Télécharger le pack complet (ZIP)</a></li>
              </ul>
              
              <p style="margin:0 0 16px;">En tant que <strong>%s</strong>, votre approbation est nécessaire pour finaliser la décision.</p>
              
              <div style="margin:22px 0 26px;">
                <a href="%s" target="_blank" style="background:#1a73e8; color:#fff; padding:12px 20px; text-decoration:none; border-radius:5px; font-size:14px; font-weight:700; display:inline-block; margin:0 10px 10px 0;">Approuver (GO)</a>
                <a href="%s" target="_blank" style="background:#fff; color:#c5221f; border:1px solid #c5221f; padding:11px 20px; text-decoration:none; border-radius:5px; font-size:14px; font-weight:700; display:inline-block;">Rejeter (NO-GO)</a>
              </div>
              
              <div style="padding-top:18px; border-top:1px solid #dadce0; font-size:12px; color:#5f6368;">
                <p style="margin:0 0 6px;">Ce lien est valable 48 h et ne peut être utilisé qu'une seule fois.</p>
                <p style="margin:0 0 6px; word-break:break-all;">Si le bouton GO ne fonctionne pas, utilisez ce lien sécurisé : <a href="%s" style="color:#1967d2;">Approuver le dossier (GO)</a></p>
                <p style="margin:0; word-break:break-all;">Pour une décision NO-GO, utilisez ce lien sécurisé : <a href="%s" style="color:#1967d2;">Rejeter le dossier (NO-GO)</a></p>
              </div>
              </div></body></html>
            """.formatted(
                dossier.getIntituleOffre(),
                safe(dossier.getClient()),
                safe(dossier.getPays()),
                formatBudgetForDisplay(dossier.getBudgetGlobal()),
                dossier.getPwinScore() != null ? String.format("%.1f", dossier.getPwinScore()) : "N/A",
                matchingResult != null && matchingResult.getTauxCouvertureCompetences() != null ? Math.round(matchingResult.getTauxCouvertureCompetences() * 100) + "%" : "N/A",
                matchingResult != null ? calculateRefTaux(matchingResult.getGapRefs()) + "%" : "N/A",
                matchingResult != null && matchingResult.getTauxCouvertureExperts() != null ? Math.round(matchingResult.getTauxCouvertureExperts() * 100) + "%" : "N/A",
                matchingResult != null && matchingResult.getAlignementStrategique() != null ? matchingResult.getAlignementStrategique() : "N/A",
                matchingResult != null && Boolean.TRUE.equals(matchingResult.getCompatibleMethodologie()) ? "<span style='color:#137333;'>Compatible</span>" : "<span style='color:#c5221f;'>Non Compatible</span>",
                dossier.getDtLimSoum() != null ? dossier.getDtLimSoum().toString() : "N/A",
                baseUrl,
                token.getToken(),
                baseUrl,
                token.getToken(),
                baseUrl,
                token.getToken(),
                token.getValidateurRole(),
                lienApprobation,
                lienRejet,
                lienApprobation,
                lienRejet
            );

        sendHtml(token.getValidateurEmail(), subject, body);
    }

    private int calculateRefTaux(String gapRefsJson) {
        if (gapRefsJson == null || gapRefsJson.isBlank() || gapRefsJson.equals("[]")) return 0;
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode array = mapper.readTree(gapRefsJson);
            if (array.isEmpty()) return 0;
            double score = 0;
            for (com.fasterxml.jackson.databind.JsonNode node : array) {
                String couvert = node.path("couvert").asText("");
                if ("OUI".equalsIgnoreCase(couvert)) score += 1;
                else if ("PARTIEL".equalsIgnoreCase(couvert)) score += 0.5;
            }
            return (int) Math.round((score / array.size()) * 100);
        } catch (Exception e) {
            return 0;
        }
    }

    // ── Email de relance ────────────────────────────────────────────────────────

    @Async
    public void sendReminderEmail(ValidationToken token, Dossier dossier) {
        String lien = baseUrl + "/validate/" + token.getToken();
        String subject = "⏰ Rappel — Validation en attente : " + truncate(dossier.getIntituleOffre(), 50);

        String body = """
            <html><body style="font-family:Arial,sans-serif;color:#1a1a1a;max-width:600px">
            <div style="background:#854F0B;padding:16px 24px;border-radius:8px 8px 0 0">
              <h2 style="color:white;margin:0;font-size:16px">⏰ Rappel — Validation en attente</h2>
            </div>
            <div style="padding:24px;background:#f9f8f5;border:1px solid #e0dcd5">
              <p>Bonjour <strong>%s</strong>,</p>
              <p>Votre validation pour le dossier <strong>%s</strong> est toujours en attente.<br>
              La date limite de soumission est le <strong>%s</strong>.</p>
              <a href="%s" style="display:inline-block;background:#0b1f3a;color:white;padding:12px 24px;
                border-radius:6px;text-decoration:none;font-weight:500;margin-top:16px">
                Accéder au dossier →
              </a>
            </div>
            </body></html>
            """.formatted(
                token.getValidateurNom() != null ? token.getValidateurNom() : "Madame/Monsieur",
                truncate(dossier.getIntituleOffre(), 80),
                dossier.getDtLimSoum() != null ? dossier.getDtLimSoum() : "N/A",
                lien
        );

        sendHtml(token.getValidateurEmail(), subject, body);
    }

    // ── Email NO-GO ciblé (DO) ──────────────────────────────────────────────────
    @Async
    public void sendNoGoEmail(ValidationToken token, Dossier dossier) {
        String lienApprobation = baseUrl + "/validate/" + token.getToken() + "?action=APPROVE_NOGO";
        String lienForcerGo    = baseUrl + "/validate/" + token.getToken() + "?action=FORCE_GO";

        String subject = "ProjectIQ — ⚠️ Alerte NO-GO proposée : " + truncate(dossier.getIntituleOffre(), 60);

        String body = """
            <html><body style="font-family:Arial,sans-serif;color:#1a1a1a;max-width:600px">
            <div style="background:#8b0000;padding:20px 24px;border-radius:8px 8px 0 0">
              <h2 style="color:white;margin:0;font-size:18px">⚠️ ProjectIQ — Décision d'abandon requise</h2>
            </div>
            <div style="padding:24px;background:#f9f8f5;border:1px solid #e0dcd5">
              <p>Bonjour <strong>%s</strong>,</p>
              <p>L'équipe d'analyse a proposé un <strong>NO-GO</strong> (abandon) pour le dossier suivant :</p>
              <table style="width:100%%;border-collapse:collapse;margin:16px 0;background:white;border-radius:6px;border:1px solid #e0dcd5">
                <tr><td style="padding:10px;font-weight:500;color:#555;width:35%%">Intitulé</td>
                    <td style="padding:10px">%s</td></tr>
                <tr style="background:#f5f4f0"><td style="padding:10px;font-weight:500;color:#555">Client</td>
                    <td style="padding:10px">%s</td></tr>
                <tr><td style="padding:10px;font-weight:500;color:#555">Pays</td>
                    <td style="padding:10px">%s</td></tr>
                <tr style="background:#f5f4f0"><td style="padding:10px;font-weight:500;color:#555">Budget</td>
                    <td style="padding:10px">%s</td></tr>
                <tr><td style="padding:10px;font-weight:500;color:#555">Score P-Win</td>
                    <td style="padding:10px"><strong>%s%%</strong></td></tr>
              </table>
              <p style="color:#555">En tant que <strong>Directeur d'Offre (DO)</strong>, veuillez valider cet abandon pour archiver le dossier, ou forcer la poursuite de l'offre (GO).</p>
              <div style="margin:28px 0;display:flex;gap:12px">
                <a href="%s" style="background:#E24B4A;color:white;padding:12px 28px;border-radius:6px;text-decoration:none;font-weight:500;font-size:15px">✗ APPROUVER L'ABANDON</a>
                &nbsp;&nbsp;
                <a href="%s" style="background:#1D9E75;color:white;padding:12px 28px;border-radius:6px;text-decoration:none;font-weight:500;font-size:15px">✓ FORCER LE GO</a>
              </div>
            </div>
            </body></html>
            """.formatted(
                token.getValidateurNom() != null ? token.getValidateurNom() : "Madame/Monsieur",
                dossier.getIntituleOffre(),
                safe(dossier.getClient()),
                safe(dossier.getPays()),
                formatBudgetForDisplay(dossier.getBudgetGlobal()),
                dossier.getPwinScore() != null ? String.format("%.1f", dossier.getPwinScore()) : "N/A",
                lienApprobation,
                lienForcerGo
        );

        sendHtml(token.getValidateurEmail(), subject, body);
    }
    
    // ── Notification analyste ───────────────────────────────────────────────────

    @Async
    public void sendAnalysteNotification(String email, Dossier dossier, String message) {
        String subject = "ProjectIQ — " + truncate(dossier.getIntituleOffre(), 60);
        String body = """
            <html><body style="font-family:Arial,sans-serif;color:#333;max-width:600px;margin:auto;">
            <div style="background:#0b1f3a;padding:20px;border-radius:8px 8px 0 0">
              <h2 style="color:white;margin:0;font-size:18px">ProjectIQ — Notification</h2>
            </div>
            <div style="padding:20px;background:#f9f8f5;border:1px solid #e0dcd5;">
                <p style="font-size:15px;line-height:1.6;">%s</p>
                <p>Dossier : <strong>%s</strong></p>
                
                <div style="margin-top:25px;margin-bottom:15px;text-align:center;">
                    <a href="%s/analyst/analyses-logs" style="background:#1D9E75;color:white;padding:12px 24px;border-radius:6px;text-decoration:none;font-weight:bold;display:inline-block;">
                        Accéder à la Plateforme
                    </a>
                </div>
            </div>
            </body></html>
            """.formatted(message, dossier.getIntituleOffre(), baseUrl);
        sendHtml(email, subject, body);
    }

    // ── Utilitaire d'envoi ──────────────────────────────────────────────────────

    private void sendHtml(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("[Email] Envoyé à {} : {}", to, subject);
        } catch (Exception e) {
            log.error("[Email] Échec envoi à {} : {}", to, e.getMessage(), e);
        }
    }

    private String safe(String v) { return v != null ? v : "N/A"; }
    
    private String formatBudgetForDisplay(String budgetGlobal) {
        if (budgetGlobal == null || budgetGlobal.isBlank()) return "N/A";
        try {
            String cleanNum = budgetGlobal.replaceAll("[^0-9.,]", "").replace(",", ".");
            if (cleanNum.lastIndexOf('.') != cleanNum.indexOf('.')) {
                cleanNum = cleanNum.substring(0, cleanNum.lastIndexOf('.')).replace(".", "") + cleanNum.substring(cleanNum.lastIndexOf('.'));
            }
            if (cleanNum.isEmpty()) return budgetGlobal;
            
            double amount = Double.parseDouble(cleanNum);
            String upper = budgetGlobal.toUpperCase();
            
            if (upper.contains("€") || upper.contains("EUR")) {
                return String.format("%s (soit env. %,.0f TND)", budgetGlobal, amount * 3.35);
            } else if (upper.contains("$") || upper.contains("USD")) {
                return String.format("%s (soit env. %,.0f TND)", budgetGlobal, amount * 3.10);
            } else if (upper.contains("£") || upper.contains("GBP")) {
                return String.format("%s (soit env. %,.0f TND)", budgetGlobal, amount * 3.90);
            }
            return budgetGlobal;
        } catch (Exception e) {
            return budgetGlobal;
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
