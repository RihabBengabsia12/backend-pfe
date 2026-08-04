package tn.rihab.authservice.controller;



import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.rihab.authservice.DTO.*;
import tn.rihab.authservice.entity.CredentialAccount;
import tn.rihab.authservice.exception.AuthException;
import tn.rihab.authservice.service.AuthService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
//@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    // --- AUTHENTICATION & REGISTRATION ---

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request, HttpServletRequest http) {
        return ResponseEntity.ok(authService.login(request, http));
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@RequestBody Map<String, String> body, HttpServletRequest http) {
        String refreshToken = body.get("refreshToken");
        log.info("🔄 Requête de rafraîchissement de token reçue");

        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new AuthException("Refresh token manquant");
        }

        return ResponseEntity.ok(authService.refreshToken(refreshToken, http));
    }

    // --- ADMINISTRATIVE ACTIONS (COMBINÉES POUR ÉVITER LE ROLLBACK) ---

    /**
     * SOLUTION FINALE : Un seul appel pour activer et assigner le rôle.
     * Cela évite le forkJoin au frontend qui causait le bug de transaction.
     */

    @PostMapping("/validate-dossier/{id}")
    public ResponseEntity<Map<String, String>> validateDossier(@PathVariable UUID id) {
        log.info("📋 Validation du dossier pour l'utilisateur {}", id);

        // On appelle la nouvelle méthode scindée dans le service
        authService.validateUserDossier(id);

        return ResponseEntity.ok(Map.of(
                "message", "Dossier validé avec succès !",
                "status", "VALIDATED"
        ));
    }

    /**
     * ÉTAPE 2 : Activer le compte (Statut : VALIDATED -> ACTIVE + ENVOI EMAIL)
     */
    @PostMapping("/activate-account/{id}")
    public ResponseEntity<Map<String, String>> activateUser(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {

        String role = body.get("role");
        log.info("🔓 Activation finale pour l'utilisateur {} avec le rôle {}", id, role);

        // On appelle la méthode d'activation finale (celle qui envoie l'email)
        authService.activateAccountFinal(id, role);

        return ResponseEntity.ok(Map.of(
                "message", "Compte activé et email de bienvenue envoyé !",
                "status", "ACTIVE"
        ));
    }

    @PutMapping("/reject/{userId}")
    public ResponseEntity<Map<String, String>> rejectAccount(
            @PathVariable String userId,
            @RequestBody Map<String, String> body
    ) {
        log.info("🚫 Requête de rejet reçue pour l'utilisateur ID : {}", userId);

        // 1. Définir l'UUID
        UUID uuid = UUID.fromString(userId);

        // 2. ECRIRE LA LIGNE ICI (Elle récupère le texte d'Angular ou met un texte par défaut)
        String reason = body.getOrDefault("reason", "Aucun motif particulier précisé.");

        // 3. Passer les deux au service
        authService.rejectUserAccount(uuid, reason);

        return ResponseEntity.ok(Map.of("message", "Le profil a été refusé et l'utilisateur notifié."));
    }

    // --- PASSWORD RECOVERY ---

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@RequestBody Map<String, String> body) {
        authService.processForgotPassword(body.get("email"));
        return ResponseEntity.ok(Map.of("message", "Lien de réinitialisation envoyé si le compte existe."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> completeReset(@RequestBody ResetPasswordRequest request) {
        authService.updatePasswordWithToken(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Mot de passe modifié avec succès."));
    }

    // --- MONITORING & LOGS ---

    @GetMapping("/events")
    public ResponseEntity<?> events(
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(authService.getEvents(email, type, page, size));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "auth-service"));
    }

    // --- LEGACY (Gardés pour compatibilité si nécessaire) ---



    @GetMapping("/me")
    public ResponseEntity<AccountResponse> me(HttpServletRequest http) {
        String email = (String) http.getAttribute("email");
        return ResponseEntity.ok(authService.getMe(email));
    }

    // À ajouter dans AuthController.java du service AUTH
    @GetMapping("/accounts")
    public ResponseEntity<Page<CredentialAccount>> getAllAccounts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "1000") int size) {
        return ResponseEntity.ok(authService.getAllAccounts(page, size));
    }


}