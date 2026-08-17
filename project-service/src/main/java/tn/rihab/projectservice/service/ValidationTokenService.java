package tn.rihab.projectservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tn.rihab.projectservice.model.entity.ValidationToken;
import tn.rihab.projectservice.repository.ValidationTokenRepository;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Arrays;
import java.util.UUID;

/** Gère les liens de validation envoyés aux managers. */
@Service
@RequiredArgsConstructor
public class ValidationTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final ValidationTokenRepository tokenRepository;

    public ValidationToken generate(UUID dossierId, String role, String email, String nom) {
        return generate(dossierId, role, email, nom, "APPROVED,REJECTED");
    }

    public ValidationToken generate(UUID dossierId, String role, String email, String nom, String allowedActions) {
        LocalDateTime now = LocalDateTime.now();
        ValidationToken token = ValidationToken.builder()
                .token(generateSecureToken())
                .dossierId(dossierId)
                .validateurRole(role)
                .validateurEmail(email)
                .validateurNom(nom)
                .status("PENDING")
                .allowedActions(allowedActions)
                .used(false)
                .createdAt(now)
                .expiresAt(now.plusHours(48))
                .build();
        return tokenRepository.save(token);
    }

    /**
     * Consomme le token de manière atomique : même deux clics simultanés ne
     * peuvent créer qu'une seule décision.
     */
    @Transactional
    public ValidationToken recordAction(String tokenStr, String action, String commentaire) {
        return recordAction(tokenStr, action, commentaire, "PLATFORM");
    }

    @Transactional
    public ValidationToken recordAction(String tokenStr, String action, String commentaire, String source) {
        ValidationToken token = tokenRepository.findByTokenForUpdate(tokenStr)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lien de validation invalide."));

        LocalDateTime now = LocalDateTime.now();
        if (!"PENDING".equals(token.getStatus()) || Boolean.TRUE.equals(token.getUsed())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce lien de validation a déjà été utilisé ou annulé.");
        }
        if (token.getExpiresAt().isBefore(now)) {
            token.setStatus("EXPIRED");
            tokenRepository.save(token);
            throw new ResponseStatusException(HttpStatus.GONE, "Ce lien de validation a expiré.");
        }
        boolean allowedAction = action != null && token.getAllowedActions() != null
                && Arrays.stream(token.getAllowedActions().split(","))
                .map(String::trim)
                .anyMatch(action::equals);
        if (!allowedAction) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Action de validation non autorisée.");
        }
        if (("REJECTED".equals(action) || "APPROVE_NOGO".equals(action))
                && (commentaire == null || commentaire.trim().length() < 10)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Une justification d'au moins 10 caractères est requise.");
        }

        boolean decisionParEmail = "EMAIL".equalsIgnoreCase(source);
        String commentaireSaisi = commentaire == null ? "" : commentaire.trim();
        String mentionSource = "Décision prise via le lien sécurisé envoyé par e-mail.";

        token.setStatus(action);
        token.setUsed(true);
        token.setCommentaire(decisionParEmail
                ? (commentaireSaisi.isBlank() ? mentionSource : commentaireSaisi + "\n" + mentionSource)
                : commentaireSaisi);
        token.setDecisionSource(decisionParEmail ? "EMAIL" : "PLATFORM");
        token.setActionAt(now);
        return tokenRepository.save(token);
    }

    public void expireOldTokens() {
        List<ValidationToken> pendingTokens = tokenRepository.findByStatusAndExpiresAtBefore("PENDING", LocalDateTime.now());
        for (ValidationToken token : pendingTokens) {
            token.setStatus("EXPIRED");
            tokenRepository.save(token);
        }
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
