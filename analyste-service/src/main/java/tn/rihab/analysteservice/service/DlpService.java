package tn.rihab.analysteservice.service;

import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service de Data Loss Prevention (DLP).
 * Intercepte et masque les données sensibles (IBAN, Numéros, Emails) 
 * avant l'envoi des documents vers l'API IA (LLM).
 * Conforme à la politique de sécurité définie pour l'UAT.
 */
@Service
@Slf4j
public class DlpService {

    // Regex simplifiées pour la démo / PFE
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\b(?:\\+33|0)[1-9](?:[\\s.-]*\\d{2}){4}\\b");
    private static final Pattern IBAN_PATTERN = Pattern.compile("\\b[A-Z]{2}\\d{2}[A-Z0-9]{4}\\d{7}([A-Z0-9]?){0,16}\\b");

    private static final String REDACTED_TEXT = "[CENSURÉ-DLP]";

    public String maskSensitiveData(String documentText) {
        if (documentText == null || documentText.isEmpty()) {
            return documentText;
        }

        String safeText = documentText;

        // Censure des emails
        safeText = EMAIL_PATTERN.matcher(safeText).replaceAll(REDACTED_TEXT);
        // Censure des numéros de téléphone
        safeText = PHONE_PATTERN.matcher(safeText).replaceAll(REDACTED_TEXT);
        // Censure des IBAN
        safeText = IBAN_PATTERN.matcher(safeText).replaceAll(REDACTED_TEXT);

        if (!safeText.equals(documentText)) {
            log.info("[DLP-POLICY] Document filtré : données sensibles masquées avant envoi à l'IA.");
        }

        return safeText;
    }
}
