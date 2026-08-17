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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final tn.rihab.projectservice.client.AnalysteServiceClient analysteServiceClient;
    private final StorageService storageService;



    @Transactional
    public Map<String, Object> getTargetManagerForBudget(double budget) { List<tn.rihab.projectservice.model.entity.Delegation> delegations = delegationRepository.findAll(); tn.rihab.projectservice.model.entity.Delegation doDel = delegations.stream().filter(d -> "DO".equals(d.getRoleName())).findFirst().orElseThrow(); tn.rihab.projectservice.model.entity.Delegation ddaDel = delegations.stream().filter(d -> "DDA".equals(d.getRoleName())).findFirst().orElseThrow(); tn.rihab.projectservice.model.entity.Delegation dgaDel = delegations.stream().filter(d -> "DGA".equals(d.getRoleName())).findFirst().orElseThrow(); tn.rihab.projectservice.model.entity.Delegation pdgDel = delegations.stream().filter(d -> "PDG".equals(d.getRoleName())).findFirst().orElseThrow(); tn.rihab.projectservice.model.entity.Delegation target = doDel; if (budget > pdgDel.getThreshold()) { target = pdgDel; } else if (budget > dgaDel.getThreshold()) { target = dgaDel; } else if (budget > ddaDel.getThreshold()) { target = ddaDel; } return Map.of("role", target.getRoleName(), "email", target.getEmail(), "threshold", target.getThreshold(), "nom", target.getDisplayName()); } 
    
    public List<Map<String, Object>> getValidationTargets(UUID dossierId, String type) {
        Dossier dossier = findDossier(dossierId);
        List<Map<String, Object>> targets = new ArrayList<>();

        if ("NOGO".equalsIgnoreCase(type)) {
            double budget = extractBudgetAmount(dossier.getBudgetGlobal());
            Map<String, Object> target = getTargetManagerForBudget(budget);
            targets.add(Map.of("role", target.get("role"), "email", target.get("email"), "nom", target.get("nom")));
        } else {
            List<tn.rihab.projectservice.model.entity.Delegation> delegations = delegationRepository.findAll();
            tn.rihab.projectservice.model.entity.Delegation doDel = delegations.stream().filter(d -> "DO".equals(d.getRoleName())).findFirst().orElseThrow();
            tn.rihab.projectservice.model.entity.Delegation ddaDel = delegations.stream().filter(d -> "DDA".equals(d.getRoleName())).findFirst().orElseThrow();
            tn.rihab.projectservice.model.entity.Delegation dgaDel = delegations.stream().filter(d -> "DGA".equals(d.getRoleName())).findFirst().orElseThrow();
            tn.rihab.projectservice.model.entity.Delegation pdgDel = delegations.stream().filter(d -> "PDG".equals(d.getRoleName())).findFirst().orElseThrow();

            // Un pack compatible est soumis simultanément à toute la chaîne de décision.
            targets.add(Map.of("role", "DO", "email", doDel.getEmail(), "nom", doDel.getDisplayName()));
            targets.add(Map.of("role", "DDA", "email", ddaDel.getEmail(), "nom", ddaDel.getDisplayName()));
            targets.add(Map.of("role", "DGA", "email", dgaDel.getEmail(), "nom", dgaDel.getDisplayName()));
            targets.add(Map.of("role", "PDG", "email", pdgDel.getEmail(), "nom", pdgDel.getDisplayName()));
        }
        return targets;
    }
    
    public List<ValidationToken> sendToValidators(UUID dossierId) {
        Dossier dossier = findDossier(dossierId);

        boolean documentsGeneres = (dossier.getApoDocxPath() != null && !dossier.getApoDocxPath().isBlank()
                && dossier.getRapportPath() != null && !dossier.getRapportPath().isBlank())
                // Le ZIP est la preuve définitive que le pack a été assemblé.
                || (dossier.getPackZipPath() != null && !dossier.getPackZipPath().isBlank());
        boolean statutAutorise = dossier.getStatus() == DossierStatus.PACK_READY
                || dossier.getStatus() == DossierStatus.REPORT_GENERATED
                || dossier.getStatus() == DossierStatus.MATCHING;

        log.info("[Validation] Contrôle pack dossier {} : status={}, apo={}, rapport={}, zip={}, documentsGeneres={}, statutAutorise={}",
                dossierId, dossier.getStatus(),
                dossier.getApoDocxPath() != null && !dossier.getApoDocxPath().isBlank(),
                dossier.getRapportPath() != null && !dossier.getRapportPath().isBlank(),
                dossier.getPackZipPath() != null && !dossier.getPackZipPath().isBlank(),
                documentsGeneres, statutAutorise);

        // RabbitMQ peut synchroniser PACK_READY avec un léger délai : les livrables
        // déjà enregistrés constituent alors une preuve suffisante pour l'envoi.
        if (!statutAutorise && !documentsGeneres) {
            throw new IllegalStateException("Le pack de soumission doit être prêt.");
        }

        // Chaque envoi ouvre un nouveau cycle de validation. Les anciennes
        // décisions restent dans l'audit, mais ne doivent jamais influencer
        // le calcul d'un nouveau « 4 GO sur 4 ».
        tokenRepository.findByDossierId(dossierId)
                .forEach(t -> {
                    t.setStatus("CANCELLED");
                    tokenRepository.save(t);
                });

        List<ValidationToken> tokens = new ArrayList<>();
        List<tn.rihab.projectservice.model.entity.Delegation> delegations = delegationRepository.findAll();
        tn.rihab.projectservice.model.entity.Delegation doDel = delegations.stream().filter(d -> "DO".equals(d.getRoleName())).findFirst().orElseThrow();
        tn.rihab.projectservice.model.entity.Delegation ddaDel = delegations.stream().filter(d -> "DDA".equals(d.getRoleName())).findFirst().orElseThrow();
        tn.rihab.projectservice.model.entity.Delegation dgaDel = delegations.stream().filter(d -> "DGA".equals(d.getRoleName())).findFirst().orElseThrow();
        tn.rihab.projectservice.model.entity.Delegation pdgDel = delegations.stream().filter(d -> "PDG".equals(d.getRoleName())).findFirst().orElseThrow();

        tokens.add(tokenService.generate(dossierId, "DO", doDel.getEmail(), doDel.getDisplayName()));
        tokens.add(tokenService.generate(dossierId, "DDA", ddaDel.getEmail(), ddaDel.getDisplayName()));
        tokens.add(tokenService.generate(dossierId, "DGA", dgaDel.getEmail(), dgaDel.getDisplayName()));
        tokens.add(tokenService.generate(dossierId, "PDG", pdgDel.getEmail(), pdgDel.getDisplayName()));
        log.info("[Validation] {} token(s) de validation persisté(s) pour le dossier {}", tokens.size(), dossierId);

        tn.rihab.projectservice.dto.MatchingResultDto matchingResult = null;
        try {
            matchingResult = analysteServiceClient.getMatchingResult(dossierId);
        } catch (Exception e) {
            log.warn("[ValidationService] Impossible de récupérer les résultats de matching pour le dossier {}", dossierId, e);
        }

        final tn.rihab.projectservice.dto.MatchingResultDto finalMatching = matchingResult;
        tokens.forEach(token -> emailService.sendValidationEmail(token, dossier, finalMatching));
        log.info("[Validation] Envoi e-mail asynchrone demandé pour le dossier {}", dossierId);

        dossier.setStatus(DossierStatus.PENDING_VALIDATION);
        dossierRepository.save(dossier);
        log.info("[Validation] Statut PENDING_VALIDATION enregistré pour le dossier {}", dossierId);

        auditTrailService.log(dossierId, "VALIDATION_SENT", "system",
                String.format("{\"validateurs\":%d}", tokens.size()), DossierStatus.PENDING_VALIDATION);
        log.info("[Validation] Audit VALIDATION_SENT enregistré pour le dossier {}", dossierId);

        return tokens;
    }

    public ValidationToken sendNoGoTargeted(UUID dossierId) {
        Dossier dossier = findDossier(dossierId);
        double budget = extractBudgetAmount(dossier.getBudgetGlobal());
        
        Map<String, Object> target = getTargetManagerForBudget(budget);
        String roleName = (String) target.get("role");
        String email = (String) target.get("email");
        
        // Annuler les anciens tokens pending
        tokenRepository.findByDossierIdAndStatus(dossierId, "PENDING")
                .forEach(t -> {
                    t.setStatus("CANCELLED");
                    tokenRepository.save(t);
                });
                
        ValidationToken token = tokenService.generate(dossierId, roleName, email, "Manager " + roleName,
                "APPROVE_NOGO,FORCE_GO");
        emailService.sendNoGoEmail(token, dossier);
        
        dossier.setManagerNotified(true);
        dossierRepository.save(dossier);
        
        auditTrailService.log(dossierId, "NOGO_VALIDATION_SENT", "system", "Ciblé: " + roleName, dossier.getStatus());
        
        return token;
    }

    @Transactional
    public String processAction(String tokenStr, String action, String commentaire) {
        return processAction(tokenStr, action, commentaire, "PLATFORM");
    }

    @Transactional
    public String processAction(String tokenStr, String action, String commentaire, String source) {
        ValidationToken token = tokenService.recordAction(tokenStr, action, commentaire, source);
        UUID dossierId = token.getDossierId();
        Dossier dossier = findDossier(dossierId);

        if ("REJECTED".equals(action)) {
            // « Rejeter (NO-GO) » est une décision finale : le dossier reste
            // visible au manager jusqu'à la génération du rapport d'audit.
            dossier.setStatus(DossierStatus.NO_GO_CONFIRMED);
            dossier.setAnalystNotified(true);
            dossierRepository.save(dossier);
            // Un seul rejet final clôt la chaîne : aucun autre validateur ne
            // doit pouvoir transformer ensuite cette décision en GO.
            tokenRepository.findByDossierIdAndStatus(dossierId, "PENDING")
                    .forEach(pending -> {
                        pending.setStatus("CANCELLED");
                        tokenRepository.save(pending);
                    });
            emailService.sendAnalysteNotification("analyste@st2i.com.tn", dossier, 
                "❌ Un manager a validé le NO-GO du dossier. Motif : " + (commentaire.isBlank() ? "Non spécifié" : commentaire)
                        + ". Le rapport d'audit final reste à générer.");
            return "La décision finale NO-GO a été enregistrée. Le rapport d'audit est en attente de génération.";
        }
        
        if ("APPROVE_NOGO".equals(action)) {
            dossier.setStatus(DossierStatus.NO_GO_CONFIRMED);
            dossier.setManagerNotified(false);
            dossierRepository.save(dossier);
            emailService.sendAnalysteNotification("analyste@st2i.com.tn", dossier, 
                "🛑 Le manager a APPROUVÉ l'abandon (NO-GO) du dossier. Il est maintenant classé sans suite.");
            return "L'abandon (NO-GO) a été validé.";
        }
        
        if ("FORCE_GO".equals(action)) {
            dossier.setStatus(DossierStatus.MATCHING); // ou DRAFTING selon la phase, on met MATCHING par defaut pour forcer l'etape d'apres
            dossier.setManagerNotified(false);
            dossierRepository.save(dossier);
            emailService.sendAnalysteNotification("analyste@st2i.com.tn", dossier, 
                "🚀 Le manager a FORCÉ LE GO sur le dossier. Vous pouvez poursuivre le traitement.");
            return "Le GO a été forcé. L'équipe a été notifiée.";
        }

        if (allValidatorsApproved(dossierId)) {
            dossier.setStatus(DossierStatus.SUBMITTED);
            dossier.setAnalystNotified(true);
            dossierRepository.save(dossier);
            emailService.sendAnalysteNotification("analyste@st2i.com.tn", dossier, 
                "✅ Toutes les validations sont obtenues. Le manager doit maintenant générer le rapport d'audit final depuis la plateforme.");
            return "Toutes les validations sont obtenues. Le rapport d'audit est en attente de génération par le manager.";
        }

        return "Approbation enregistrée.";
    }

    private boolean allValidatorsApproved(UUID dossierId) {
        List<ValidationToken> actifs = tokenRepository.findByDossierId(dossierId).stream()
                .filter(t -> !"CANCELLED".equals(t.getStatus()) && !"EXPIRED".equals(t.getStatus()))
                .toList();
        boolean allApproved = !actifs.isEmpty() && actifs.stream().allMatch(t -> "APPROVED".equals(t.getStatus()));
        log.info("[Validation] Décision dossier {} : tokens actifs={}, statuts={}, tous approuvés={}",
                dossierId, actifs.size(), actifs.stream().map(ValidationToken::getStatus).toList(), allApproved);
        return allApproved;
    }

    private double extractBudgetAmount(String budgetGlobal) {
        if (budgetGlobal == null || budgetGlobal.isBlank()) return 0;
        try {
            String cleanNum = budgetGlobal.replaceAll("[^0-9.,]", "").replace(",", ".");
            if (cleanNum.lastIndexOf('.') != cleanNum.indexOf('.')) {
                cleanNum = cleanNum.substring(0, cleanNum.lastIndexOf('.')).replace(".", "") + cleanNum.substring(cleanNum.lastIndexOf('.'));
            }
            if (cleanNum.isEmpty()) return 0;
            
            double amount = Double.parseDouble(cleanNum);
            String upper = budgetGlobal.toUpperCase();
            
            if (upper.contains("€") || upper.contains("EUR")) return amount * 3.35;
            if (upper.contains("$") || upper.contains("USD")) return amount * 3.10;
            if (upper.contains("£") || upper.contains("GBP")) return amount * 3.90;
            
            return amount;
        } catch (Exception e) { return 0; }
    }

    private Dossier findDossier(UUID id) {
        return dossierRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Dossier non trouvé"));
    }

    /**
     * Accès aux livrables depuis l'e-mail.
     * Le token est à usage unique pour la décision, pas pour la lecture des documents :
     * le manager peut donc encore relire le pack après son GO/NO-GO, jusqu'à expiration.
     */
    @Transactional(readOnly = true)
    public SecuredDocument getDocumentForValidationToken(String tokenValue, String type) {
        ValidationToken token = tokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Lien de validation invalide."));
        if (token.getExpiresAt() == null || !token.getExpiresAt().isAfter(java.time.LocalDateTime.now())) {
            throw new IllegalStateException("Ce lien de consultation a expiré.");
        }

        Dossier dossier = findDossier(token.getDossierId());
        String normalizedType = type == null ? "" : type.toLowerCase();
        String path = switch (normalizedType) {
            case "rapport" -> dossier.getRapportPath();
            case "apo" -> dossier.getApoDocxPath();
            case "pack" -> dossier.getPackZipPath();
            default -> throw new IllegalArgumentException("Document non autorisé.");
        };
        if (path == null || path.isBlank()) throw new IllegalStateException("Le document demandé n'est pas disponible.");

        try (java.io.InputStream stream = storageService.getStream(path)) {
            byte[] content = stream.readAllBytes();
            String filename = switch (normalizedType) {
                case "rapport" -> "Rapport_General.docx";
                case "apo" -> "Rapport_APO.docx";
                default -> "Pack_Decisionnel.zip";
            };
            String contentType = "pack".equals(normalizedType) ? "application/zip"
                    : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            return new SecuredDocument(content, filename, contentType);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Lecture du document impossible.", e);
        }
    }

    public record SecuredDocument(byte[] content, String filename, String contentType) {}

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
        if (email == null || email.isBlank()) return List.of();

        List<ValidationToken> managerTokens = tokenRepository.findByValidateurEmailIgnoreCase(email.trim());
        Map<UUID, ValidationToken> oneTokenPerDossier = new LinkedHashMap<>();
        managerTokens.stream()
                .filter(t -> !"CANCELLED".equals(t.getStatus()) && !"EXPIRED".equals(t.getStatus()))
                .forEach(t -> oneTokenPerDossier.putIfAbsent(t.getDossierId(), t));

        return oneTokenPerDossier.values().stream().map(t -> {
            Dossier d = dossierRepository.findById(t.getDossierId()).orElse(null);
            if (d == null) return null;
            // AUDIT signifie « génération en cours » : le manager doit encore
            // voir le dossier. Il disparaît seulement après ARCHIVED.
            if (d.getStatus() == DossierStatus.ARCHIVED) return null;
            List<ValidationToken> allTokens = tokenRepository.findByDossierId(t.getDossierId()).stream()
                    .filter(v -> !"CANCELLED".equals(v.getStatus()) && !"EXPIRED".equals(v.getStatus()))
                    .toList();
            List<Map<String, Object>> decisions = allTokens.stream().map(v -> Map.<String, Object>of(
                    "role", v.getValidateurRole(),
                    "nom", v.getValidateurNom() != null ? v.getValidateurNom() : v.getValidateurRole(),
                    "status", v.getStatus(),
                    "source", v.getDecisionSource() != null ? v.getDecisionSource() : "PLATFORM",
                    "commentaire", v.getCommentaire() != null ? v.getCommentaire() : ""
            )).toList();
            boolean noGo = allTokens.stream().anyMatch(v ->
                    "REJECTED".equals(v.getStatus()) || "APPROVE_NOGO".equals(v.getStatus()));
            boolean goFinal = !allTokens.isEmpty() && allTokens.stream().allMatch(v -> "APPROVED".equals(v.getStatus()));
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
            map.put("decisionStatus", t.getStatus());
            map.put("decisionSource", t.getDecisionSource() != null ? t.getDecisionSource() : "PLATFORM");
            map.put("decisionAt", t.getActionAt() != null ? t.getActionAt().toString() : null);
            map.put("decisionCommentaire", t.getCommentaire() != null ? t.getCommentaire() : "");
            map.put("decisions", decisions);
            map.put("approvedBy", decisions.stream().filter(v -> "APPROVED".equals(v.get("status")))
                    .map(v -> v.get("nom")).toList());
            map.put("rejectedBy", decisions.stream().filter(v ->
                            "REJECTED".equals(v.get("status")) || "APPROVE_NOGO".equals(v.get("status")))
                    .map(v -> v.get("nom")).toList());
            map.put("decisionSummary", noGo ? "NO_GO_CONFIRMED" : (goFinal ? "GO_CONFIRMED" : "IN_PROGRESS"));
            map.put("showGenerateAudit", d.getStatus() == DossierStatus.SUBMITTED
                    || d.getStatus() == DossierStatus.NO_GO_CONFIRMED);
            map.put("auditInProgress", d.getStatus() == DossierStatus.AUDIT);
            return map;
        }).filter(java.util.Objects::nonNull).toList();
    }

    @Transactional
    public void generateAuditForManager(UUID dossierId) {
        Dossier dossier = findDossier(dossierId);
        if (dossier.getStatus() != DossierStatus.SUBMITTED
                && dossier.getStatus() != DossierStatus.NO_GO_CONFIRMED
                && dossier.getStatus() != DossierStatus.AUDIT) {
            throw new IllegalStateException("Le rapport d'audit ne peut être généré qu'après une décision finale GO ou NO-GO.");
        }
        DossierStatus previousStatus = dossier.getStatus();
        boolean retry = previousStatus == DossierStatus.AUDIT;
        if (!retry) {
            dossier.setStatus(DossierStatus.AUDIT);
            dossierRepository.save(dossier);
            auditTrailService.log(dossierId, "AUDIT_REQUESTED", "manager",
                    "{\"source\":\"PLATFORM\",\"action\":\"GENERATE_AUDIT\"}", DossierStatus.AUDIT);
        }
        try {
            // Le lancement est direct : le dossier ne doit pas rester AUDIT si
            // un message de déclenchement RabbitMQ est perdu avant l'analyste.
            // Le retour AUDIT_GENERATED demeure asynchrone et archive le dossier.
            analysteServiceClient.generateAudit(dossierId);
            log.info("[Validation] Génération d'audit demandée à analyste-service pour {}", dossierId);
        } catch (Exception e) {
            if (!retry) {
                dossier.setStatus(previousStatus);
                dossierRepository.save(dossier);
            }
            log.error("[Validation] Impossible de lancer l'audit pour {} : {}", dossierId, e.getMessage(), e);
            throw new IllegalStateException("Le service de génération d'audit est indisponible. La décision finale est conservée.", e);
        }
    }
}
