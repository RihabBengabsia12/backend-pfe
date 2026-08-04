package tn.rihab.projectservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.rihab.projectservice.model.entity.ValidationToken;
import tn.rihab.projectservice.repository.ValidationTokenRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ValidationTokenService {

    private final ValidationTokenRepository tokenRepository;

    public ValidationToken generate(UUID dossierId, String role, String email, String nom) {
        ValidationToken token = ValidationToken.builder()
                .token(UUID.randomUUID().toString())
                .dossierId(dossierId)
                .validateurRole(role)
                .validateurEmail(email)
                .validateurNom(nom)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(48)) // Expiration à 48h
                .build();
        return tokenRepository.save(token);
    }

    public ValidationToken recordAction(String tokenStr, String action, String commentaire) {
        ValidationToken token = tokenRepository.findByToken(tokenStr)
                .orElseThrow(() -> new IllegalArgumentException("Token invalide ou inexistant"));

        token.setStatus(action);
        token.setCommentaire(commentaire); // Sauvegarde le motif du rejet si présent
        token.setActionAt(LocalDateTime.now());

        return tokenRepository.save(token);
    }

    public void expireOldTokens() {
        List<ValidationToken> pendingTokens = tokenRepository.findByStatusAndExpiresAtBefore("PENDING", LocalDateTime.now());
        for (ValidationToken token : pendingTokens) {
            token.setStatus("EXPIRED");
            tokenRepository.save(token);
        }
    }
}