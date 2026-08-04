package tn.rihab.adminservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.rihab.adminservice.DTO.*;
import tn.rihab.adminservice.service.UserService;
import tn.rihab.adminservice.security.SecurityUtils;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UserController {

    private final UserService userService;

    /**
     * Liste tous les utilisateurs (y compris les GUEST pour validation)
     */
    @GetMapping
    public ResponseEntity<Page<UserResponse>> findAll(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<UserResponse> userPage = userService.findAll(page, size);
        // On applique le patch pour que l'admin système soit bien reconnu
        userPage.getContent().forEach(this::applyAdminPatch);
        return ResponseEntity.ok(userPage);
    }

    /**
     * Assigne un rôle (ex: transformer un GUEST en ANALYST)
     */
    @PatchMapping("/{id}/role")
    public ResponseEntity<?> assignRole(@PathVariable UUID id, @RequestBody RoleRequest request) {
        try {
            // Remplace "admin@st2i.tn" par l'email de ton admin actuel
            UserResponse response = userService.assignRoleByActorEmail(id, request.getRoleCode(), "admin@st2i.tn");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            // CECI VA T'AFFICHER LE VRAI MESSAGE DANS POSTMAN
            return ResponseEntity.status(500).body("Erreur réelle : " + e.getMessage());
        }
    }
    /**
     * Active ou désactive un compte (Bannir/Valider)
     */
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<UserResponse> toggle(@PathVariable UUID id) {
        String actorEmail = SecurityUtils.getCurrentUserEmail();
        return ResponseEntity.ok(userService.toggleStatusByActorEmail(id, actorEmail));
    }

    /**
     * Supprime un utilisateur définitivement
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable UUID id) {
        String actorEmail = SecurityUtils.getCurrentUserEmail();
        userService.deleteByActorEmail(id, actorEmail);
        return ResponseEntity.ok(Map.of("message", "Utilisateur supprimé avec succès"));
    }

    /**
     * Identifie l'admin système dans les réponses JSON
     */
    private void applyAdminPatch(UserResponse user) {
        // Mise à jour avec ton email admin de st2i.tn
        if (user != null && "admin@st2i.tn".equals(user.getEmail())) {
            user.setRole("ADMIN");
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> findById(@PathVariable UUID id) {
        UserResponse response = userService.findById(id);
        applyAdminPatch(response);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "admin-service"));
    }
}