package tn.rihab.projectservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.rihab.projectservice.messaging.EventPublisher;
import tn.rihab.projectservice.model.DossierStatus;
import tn.rihab.projectservice.model.entity.Dossier;
import tn.rihab.projectservice.model.entity.ValidationToken;
import tn.rihab.projectservice.repository.DossierRepository;
import tn.rihab.projectservice.repository.ValidationTokenRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationService {

    private final DossierRepository         dossierRepository;
    private final ValidationTokenRepository tokenRepository;
    private final ValidationTokenService    tokenService; // <--- CORRIGÉ : Utilise ton service métier
    private final EmailService              emailService;
    private final EventPublisher            eventPublisher;
    private final AuditTrailService         auditTrailService;
    private final tn.rihab.projectservice.repository.DelegationRepository delegationRepository;



    @Transactional
    public List<ValidationToken> sendToValidators(UUID dossierId) {
        Dossier dossier = findDossier(dossierId);

        if (dossier.getStatus() != DossierStatus.PACK_READY) {
            throw new IllegalStateException("Le pack de soumission doit être prêt.");
        }

        tokenRepository.findByDossierIdAndStatus(dossierId, "PENDING")
                .forEach(t -> {
                    t.setStatus("CANCELLED");
                    tokenRepository.save(t);
                });

        List<ValidationToken> tokens = new ArrayList<>();
        double budget = extractBudgetAmount(dossier.getBudgetGlobal());

        List<tn.rihab.projectservice.model.entity.Delegation> delegations = delegationRepository.findAll();
        tn.rihab.projectservice.model.entity.Delegation doDel = delegations.stream().filter(d -> "DO".equals(d.getRoleName())).findFirst().orElseThrow();
        tn.rihab.projectservice.model.entity.Delegation ddaDel = delegations.stream().filter(d -> "DDA".equals(d.getRoleName())).findFirst().orElseThrow();
        tn.rihab.projectservice.model.entity.Delegation dgaDel = delegations.stream().filter(d -> "DGA".equals(d.getRoleName())).findFirst().orElseThrow();
        tn.rihab.projectservice.model.entity.Delegation pdgDel = delegations.stream().filter(d -> "PDG".equals(d.getRoleName())).findFirst().orElseThrow();

        tokens.add(tokenService.generate(dossierId, "DO", doDel.getEmail(), doDel.getDisplayName()));
        if (budget > ddaDel.getThreshold()) tokens.add(tokenService.generate(dossierId, "DDA", ddaDel.getEmail(), ddaDel.getDisplayName()));
        if (budget > dgaDel.getThreshold()) tokens.add(tokenService.generate(dossierId, "DGA", dgaDel.getEmail(), dgaDel.getDisplayName()));
        if (budget > pdgDel.getThreshold()) tokens.add(tokenService.generate(dossierId, "PDG", pdgDel.getEmail(), pdgDel.getDisplayName()));

        tokens.forEach(token -> emailService.sendValidationEmail(token, dossier));

        dossier.setStatus(DossierStatus.PENDING_VALIDATION);
        dossierRepository.save(dossier);

        auditTrailService.log(dossierId, "VALIDATION_SENT", "system",
                String.format("{\"validateurs\":%d}", tokens.size()), DossierStatus.PENDING_VALIDATION);

        return tokens;
    }

    @Transactional
    public String processAction(String tokenStr, String action, String commentaire) {
        ValidationToken token = tokenService.recordAction(tokenStr, action, commentaire);
        UUID dossierId = token.getDossierId();
        Dossier dossier = findDossier(dossierId);

        if ("REJECTED".equals(action)) {
            dossier.setStatus(DossierStatus.PACK_READY);
            dossier.setAnalystNotified(true);
            dossierRepository.save(dossier);
            emailService.sendAnalysteNotification("analyste@st2i.com.tn", dossier, 
                "❌ Un manager a REJETÉ votre Pack. Motif : " + (commentaire.isBlank() ? "Non spécifié" : commentaire));
            return "Votre rejet a été enregistré.";
        }

        if (allValidatorsApproved(dossierId)) {
            dossier.setStatus(DossierStatus.SUBMITTED);
            dossier.setAnalystNotified(true);
            dossierRepository.save(dossier);
            eventPublisher.publishDossierSubmitted(dossierId);
            emailService.sendAnalysteNotification("analyste@st2i.com.tn", dossier, 
                "✅ Félicitations ! Votre Pack a été entièrement validé par la Direction et est prêt à être envoyé au client.");
            return "Toutes les validations sont obtenues.";
        }

        return "Approbation enregistrée.";
    }

    private boolean allValidatorsApproved(UUID dossierId) {
        List<ValidationToken> actifs = tokenRepository.findByDossierId(dossierId).stream()
                .filter(t -> !"CANCELLED".equals(t.getStatus()))
                .toList();
        return !actifs.isEmpty() && actifs.stream().allMatch(t -> "APPROVED".equals(t.getStatus()));
    }

    private double extractBudgetAmount(String budgetGlobal) {
        try {
            return budgetGlobal == null ? 0 : Double.parseDouble(budgetGlobal.replaceAll("[^0-9.]", ""));
        } catch (Exception e) { return 0; }
    }

    private Dossier findDossier(UUID id) {
        return dossierRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Dossier non trouvé"));
    }

    // Ajoute ceci dans ValidationService.java pour satisfaire le contrôleur
    public List<ValidationToken> getValidationStatus(UUID dossierId) {
        return tokenRepository.findAllByDossierIdOrdered(dossierId);
    }

    @Transactional
    public void sendReminder(UUID dossierId, String role) {
        Dossier dossier = findDossier(dossierId);
        tokenRepository.findByDossierIdAndStatus(dossierId, "PENDING").stream()
                .filter(t -> role.equals(t.getValidateurRole()))
                .findFirst()
                .ifPresentOrElse(
                        t -> emailService.sendReminderEmail(t, dossier),
                        () -> { throw new IllegalArgumentException("Aucun token PENDING pour le rôle : " + role); }
                );
        auditTrailService.log(dossierId, "REMINDER_SENT", "user", "{\"role\":\"" + role + "\"}", null);
    }

    @Scheduled(fixedDelay = 3_600_000)
    public void expireOldTokens() {
        tokenService.expireOldTokens();
    }

    public List<java.util.Map<String, Object>> getPendingValidationsForManager(String email) {
        List<ValidationToken> pendingTokens = tokenRepository.findByValidateurEmailAndStatus(email, "PENDING");
        return pendingTokens.stream().map(t -> {
            Dossier d = dossierRepository.findById(t.getDossierId()).orElse(null);
            if (d == null) return null;
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("token", t.getToken());
            map.put("dossierId", d.getId());
            map.put("intituleOffre", d.getIntituleOffre());
            map.put("client", d.getClient());
            map.put("budgetGlobal", d.getBudgetGlobal());
            map.put("pwinScore", d.getPwinScore());
            map.put("hommesMois", d.getHommesMois());
            map.put("dtLimSoum", d.getDtLimSoum() != null ? d.getDtLimSoum().toString() : null);
            map.put("packZipPath", d.getPackZipPath());
            map.put("createdAt", t.getCreatedAt().toString());
            map.put("role", t.getValidateurRole());
            return map;
        }).filter(java.util.Objects::nonNull).toList();
    }
}