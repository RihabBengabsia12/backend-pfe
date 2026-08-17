package tn.rihab.projectservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tn.rihab.projectservice.model.entity.AnonymizationToken;
import tn.rihab.projectservice.repository.AnonymizationTokenRepository;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnonymizationService {

    private final AnonymizationTokenRepository tokenRepository;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\b(?:\\+33|0|\\+216)[\\s.-]*[1-9](?:[\\s.-]*\\d){7,11}\\b");
    private static final Pattern IBAN_PATTERN = Pattern.compile("\\b[A-Z]{2}\\d{2}[A-Z0-9]{4}\\d{7}([A-Z0-9]?){0,16}\\b");
    
    // Pattern to catch tokens like [DLP-EMAIL-8f3...] during unmasking
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\[DLP-[A-Z]+-[a-f0-9\\-]{36}\\]");

    public String maskText(String documentText) {
        if (documentText == null || documentText.isEmpty()) {
            return documentText;
        }

        String safeText = maskWithPattern(documentText, EMAIL_PATTERN, "EMAIL");
        safeText = maskWithPattern(safeText, PHONE_PATTERN, "PHONE");
        safeText = maskWithPattern(safeText, IBAN_PATTERN, "IBAN");

        if (!safeText.equals(documentText)) {
            log.info("[DLP-POLICY] Document filtré : données sensibles masquées et enregistrées dans le coffre-fort.");
        }
        return safeText;
    }

    private String maskWithPattern(String text, Pattern pattern, String type) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String originalValue = matcher.group();
            String tokenStr = "[DLP-" + type + "-" + UUID.randomUUID().toString() + "]";
            
            AnonymizationToken token = AnonymizationToken.builder()
                    .token(tokenStr)
                    .originalValue(originalValue)
                    .build();
            tokenRepository.save(token);
            
            matcher.appendReplacement(sb, Matcher.quoteReplacement(tokenStr));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public Map<String, String> unmaskMap(Map<String, String> values) {
        if (values == null) return null;
        values.forEach((k, v) -> {
            if (v != null) {
                values.put(k, unmaskText(v));
            }
        });
        return values;
    }

    public String unmaskText(String maskedText) {
        if (maskedText == null || maskedText.isEmpty()) {
            return maskedText;
        }

        Matcher matcher = TOKEN_PATTERN.matcher(maskedText);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String tokenStr = matcher.group();
            AnonymizationToken token = tokenRepository.findById(tokenStr).orElse(null);
            if (token != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(token.getOriginalValue()));
            } else {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(tokenStr));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
