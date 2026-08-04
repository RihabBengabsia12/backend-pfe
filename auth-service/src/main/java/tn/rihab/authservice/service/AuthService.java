package tn.rihab.authservice.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.rihab.authservice.DTO.*;
import tn.rihab.authservice.config.RabbitMQConfig;
import tn.rihab.authservice.entity.AccessEvent;
import tn.rihab.authservice.entity.CredentialAccount;
import tn.rihab.authservice.entity.RefreshToken;
import tn.rihab.authservice.exception.AuthException;
import tn.rihab.authservice.repository.AccessEventRepository;
import tn.rihab.authservice.repository.CredentialAccountRepository;
import tn.rihab.authservice.repository.RefreshTokenRepository;
import tn.rihab.authservice.security.JwtService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final CredentialAccountRepository accountRepo;
    private final RefreshTokenRepository refreshRepo;
    private final AccessEventRepository eventRepo;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final RabbitTemplate rabbitTemplate;
    private final AuditService auditService;

    /**
     * REGISTER : Envoie vers Admin-Service via RabbitMQ
     */
    public String register(RegisterRequest request) {

        // 1. Préparation des variables pour comparaison
        String email = request.getEmail().toLowerCase().trim();
        String password = request.getPassword();
        String fullName = request.getFullName().toLowerCase().trim().replace(" ", "");
        String emailPrefix = email.split("@")[0];

        // 2. CAUSE : Email déjà utilisé
        if (accountRepo.findByEmail(email).isPresent()) {
            return "CAUSE_EMAIL_EXISTE";
        }

        // 3. CAUSE : Mot de passe trop court
        if (password.length() < 8) {
            return "CAUSE_MDP_COURT";
        }

        // 4. CAUSE : Mot de passe contient le Nom ou l'Email (Sécurité)
        if (password.toLowerCase().contains(fullName) || password.toLowerCase().contains(emailPrefix)) {
            return "CAUSE_MDP_TROP_SIMPLE";
        }



        CredentialAccount account = new CredentialAccount();
        account.setEmail(request.getEmail());
        account.setPasswordHash(encoder.encode(request.getPassword()));
        account.setRole("GUEST");
        account.setAccountStatus("PENDING");
        account.setUserId(UUID.randomUUID());
        account.setCreatedAt(OffsetDateTime.now());

        accountRepo.save(account);

        auditService.saveAudit(account.getUserId(), account.getEmail(), "CRÉATION", "SUCCESS", "Compte créé en attente de validation", null);

        try {
            UserSyncDTO syncData = new UserSyncDTO(
                    account.getUserId(),
                    account.getEmail(),
                    request.getFullName(),
                    "GUEST"
            );

            log.info("📤 [RABBITMQ] Synchro vers Admin-Service : {}", account.getEmail());

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.USER_EXCHANGE,
                    RabbitMQConfig.USER_ROUTING_KEY,
                    syncData
            );

            log.info("✅ [RABBITMQ] Données envoyées");
        } catch (Exception e) {
            log.error("❌ [RABBITMQ] Erreur synchro : {}", e.getMessage());
        }

        return "Inscription réussie. Votre compte est en attente de validation par l'administrateur.";
    }

    /**
     * LOGIN
     */
    public LoginResponse login(LoginRequest request, HttpServletRequest http) {
        CredentialAccount account = accountRepo.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    auditService.saveAudit(null, request.getEmail(), "LOGIN_FAILURE", "FAILURE", "Email inexistant", http);
                    return new AuthException("Identifiants incorrects");
                });

        if ("PENDING".equals(account.getAccountStatus())) {
            auditService.saveAudit(account.getUserId(), account.getEmail(), "LOGIN_BLOCKED", "FAILURE", "En attente", http);
            throw new AuthException("Votre compte est en attente de validation.");
        }

        if ("REJECTED".equals(account.getAccountStatus()) || "BANNED".equals(account.getAccountStatus())) {
            auditService.saveAudit(account.getUserId(), account.getEmail(), "LOGIN_BLOCKED", "FAILURE", "Accès refusé", http);
            throw new AuthException("Ce compte a été refusé ou désactivé.");
        }

        if (!encoder.matches(request.getPassword(), account.getPasswordHash())) {
            auditService.saveAudit(account.getUserId(), account.getEmail(), "LOGIN_FAILURE", "FAILURE", "Password incorrect", http);
            throw new AuthException("Identifiants incorrects");
        }

        auditService.saveAudit(account.getUserId(), account.getEmail(), "LOGIN_SUCCESS", "SUCCESS", "Connecté", http);

        String role = (account.getRole() == null) ? "GUEST" : account.getRole();
        String accessToken = jwtService.generateAccessToken(account.getEmail(), role);
        String rawRefresh = UUID.randomUUID().toString();

        RefreshToken rt = RefreshToken.builder()
                .userId(account.getUserId())
                .tokenHash(sha256(rawRefresh))
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .build();
        refreshRepo.save(rt);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefresh)
                .email(account.getEmail())
                .role(role)
                .build();
    }

    /**
     * VALIDATION & ACTIVATION
     */
    public void validateUserDossier(UUID id) {
        CredentialAccount account = accountRepo.findById(id).orElseThrow(() -> new AuthException("Compte non trouvé"));
        account.setAccountStatus("VALIDATED");
        accountRepo.save(account);

        String roleName = account.getRole();

        // 1. Tu prépares le sujet et le message ici
        String subject = "Profil Validé - PROJECT IQ";
        String message = "Bonjour,\n\n"
                + "Nous vous informons que votre profil a été validé par l'administration.\n"
                + "Votre rôle attribué : " + roleName + "\n\n"
                + "Vous pouvez désormais vous connecter à votre espace personnel.\n"
                + "L'équipe PROJECT IQ.";

        // 2. CORRECTION : Utilise les variables 'subject' et 'message' au lieu du texte entre guillemets
        //sendEmail(account.getEmail(), subject, message);

        auditService.saveAudit(account.getUserId(), account.getEmail(), "VALIDATION_DOSSIER", "SUCCESS", "Validé", null);
    }

    public void activateAccountFinal(UUID id, String role) {
        CredentialAccount account = accountRepo.findById(id)
                .orElseThrow(() -> new AuthException("Compte non trouvé"));

        // 1. Définition du rôle et mise à jour du statut
        String selectedRole = (role != null && !role.isEmpty()) ? role : "USER";
        account.setAccountStatus("ACTIVE");
        account.setRole(selectedRole);
        accountRepo.save(account);

        // 2. Préparation de l'email professionnel (Profil au lieu de Dossier)
        String subject  = "Accès activé : Bienvenue sur la plateforme ProjectIQ";

        String message = "Bonjour,\n\n"
                + "Nous vous informons que votre compte sur la plateforme ProjectIQ est désormais opérationnel.\n\n"
                + "Votre profil a été validé avec le rôle suivant : " + selectedRole + ".\n\n"
                + "Vous pouvez dès à présent vous connecter à votre espace personnel en utilisant vos identifiants habituels.\n\n"
                + "Cordialement,\n\n"
                + "L'Équipe Support ProjectIQ";

        // 3. Envoi via RabbitMQ
        sendEmail(account.getEmail(), subject, message);

        // 4. Audit
        auditService.saveAudit(account.getUserId(), account.getEmail(), "ACTIVATION_FINALE", "SUCCESS", "Profil activé", null);
    }

    public void rejectUserAccount(UUID userId, String reason) {
        CredentialAccount account = accountRepo.findByUserId(userId).orElseThrow();
        account.setAccountStatus("REJECTED");
        accountRepo.save(account);

        String subject = "Mise à jour de votre profil - PROJECT IQ";
        String body = "Bonjour,\n\n"
                + "Après étude de vos informations, nous vous informons que votre profil n'a pas été validé pour la raison suivante :\n"
                + "----------------------------\n"
                + "\"" + reason + "\"\n"
                + "----------------------------\n\n"
                + "Cordialement,\n"
                + "L'équipe d'administration PROJECT IQ.";

        // CORRECTION ICI : on utilise 'subject' et 'body'
        sendEmail(account.getEmail(), subject, body);

        try {
            Map<String, String> syncMessage = new HashMap<>();
            syncMessage.put("email", account.getEmail());
            syncMessage.put("status", "REJECTED");

            // On utilise les mêmes noms d'exchange que pour la création
            rabbitTemplate.convertAndSend("user.sync.exchange", "user.sync.routing.key", syncMessage);

            log.info("✅ Notification de refus envoyée à admin-service pour : {}", account.getEmail());
        } catch (Exception e) {
            log.error("❌ Erreur de synchronisation RabbitMQ : {}", e.getMessage());
        }
    }


    private void sendEmail(String to, String subject, String body) {
        try {
            EmailNotificationDTO emailMessage = new EmailNotificationDTO();
            emailMessage.setTo(to);
            emailMessage.setSubject(subject);
            emailMessage.setMessage(body);
            rabbitTemplate.convertAndSend(RabbitMQConfig.MAIL_EXCHANGE, RabbitMQConfig.MAIL_ROUTING_KEY, emailMessage);
        } catch (Exception e) {
            log.error("❌ Erreur RabbitMQ Email : {}", e.getMessage());
        }
    }

    // --- GESTION MOT DE PASSE & TOKENS ---

    public void processForgotPassword(String email) {
        accountRepo.findByEmail(email).ifPresent(account -> {
            String token = UUID.randomUUID().toString();
            account.setResetToken(token);
            accountRepo.save(account);
            String link = "http://localhost:4200/reset-password?token=" + token;
            sendEmail(email, "Réinitialisation", "Lien : " + link);
        });
    }

    /**
     * ✅ AJOUTÉ : Cette méthode corrige ton erreur de compilation Gradle
     */
    public void updatePasswordWithToken(String token, String newPassword) {
        log.info("🔄 Reset password pour le token: {}", token);
        CredentialAccount account = accountRepo.findByResetToken(token)
                .orElseThrow(() -> new AuthException("Token invalide ou expiré"));
        
        account.setPasswordHash(encoder.encode(newPassword));
        account.setResetToken(null);
        accountRepo.save(account);
        
        auditService.saveAudit(account.getUserId(), account.getEmail(), "PASSWORD_RESET", "SUCCESS", "Mot de passe réinitialisé", null);
    }

    public void resetPassword(UUID userId, String newPassword) {
        CredentialAccount account = accountRepo.findByUserId(userId).orElseThrow();
        account.setPasswordHash(encoder.encode(newPassword));
        accountRepo.save(account);
    }

    public LoginResponse refreshToken(String rawRefreshToken, HttpServletRequest http) {
        RefreshToken rt = refreshRepo.findByTokenHash(sha256(rawRefreshToken))
                .filter(t -> t.getExpiresAt().isAfter(OffsetDateTime.now()))
                .orElseThrow(() -> new AuthException("Token expiré"));
        CredentialAccount account = accountRepo.findByUserId(rt.getUserId()).orElseThrow();
        return LoginResponse.builder()
                .accessToken(jwtService.generateAccessToken(account.getEmail(), account.getRole()))
                .refreshToken(rawRefreshToken).email(account.getEmail()).role(account.getRole()).build();
    }

    public void logout(String rawRefreshToken, HttpServletRequest http) {
        refreshRepo.findByTokenHash(sha256(rawRefreshToken)).ifPresent(rt -> {
            rt.revoke();
            refreshRepo.save(rt);
        });
    }

    public AccountResponse getMe(String email) {
        CredentialAccount account = accountRepo.findByEmail(email).orElseThrow();
        return AccountResponse.builder().id(account.getUserId()).email(account.getEmail()).role(account.getRole()).accountStatus(account.getAccountStatus()).build();
    }

    public Page<AccessEvent> getEvents(String email, String type, int page, int size) {
        return eventRepo.findAll(PageRequest.of(page, size, Sort.by("occurredAt").descending()));
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    /**
     * Récupère tous les comptes inscrits dans la base de l'authentification.
     * Cette méthode permet de voir les utilisateurs même en cas de problème de synchronisation.
     */
    /**
     * Récupère tous les comptes inscrits dans la base de l'authentification.
     * Utilise l'entité CredentialAccount.
     */
    public Page<CredentialAccount> getAllAccounts(int page, int size) {
        // Tri par date de création (vérifiez que le champ 'createdAt' existe dans CredentialAccount,
        // sinon utilisez 'id' ou un autre champ existant).
        Pageable pageable = PageRequest.of(page, size, Sort.by("email").ascending());

        return accountRepo.findAll(pageable);
    }


}