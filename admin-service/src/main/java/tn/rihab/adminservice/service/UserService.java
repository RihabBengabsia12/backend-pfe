package tn.rihab.adminservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.rihab.adminservice.DTO.*;
import tn.rihab.adminservice.entity.*;
import tn.rihab.adminservice.exception.AdminException;
import tn.rihab.adminservice.repository.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final AppUserRepository userRepo;
    private final RoleRepository roleRepo;
    private final AuditService auditService;

    // ❌ SUPPRIME : private final DataEventRepository eventRepo;

    @Transactional(readOnly = true)
    public Page<UserResponse> findAll(int page, int size) {
        return userRepo.findAll(PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        return userRepo.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> new AdminException("Utilisateur introuvable"));
    }

    @Transactional(readOnly = true)
    public UserResponse findByEmail(String email) {
        return userRepo.findByEmail(email)
                .map(UserResponse::from)
                .orElseThrow(() -> new AdminException("Utilisateur introuvable : " + email));
    }

    @Transactional
    public UserResponse toggleStatusByActorEmail(UUID id, String actorEmail) {
        AppUser user = userRepo.findById(id).orElseThrow(() -> new AdminException("User introuvable"));
        String oldStatus = user.getStatus();
        user.setStatus("ACTIVE".equals(oldStatus) ? "DISABLED" : "ACTIVE");
        AppUser saved = userRepo.saveAndFlush(user);
        auditService.saveAudit(id, "AppUser", "TOGGLE_STATUS", oldStatus, user.getStatus());
        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse assignRoleByActorEmail(UUID id, String roleCode, String actorEmail) {
        AppUser user = userRepo.findById(id).orElseThrow(() -> new AdminException("Utilisateur cible introuvable"));
        Role role = roleRepo.findByCode(roleCode).orElseThrow(() -> new AdminException("Rôle " + roleCode + " introuvable"));
        String oldRole = user.getRoles().isEmpty() ? "NONE" : user.getRoles().iterator().next().getCode();
        user.getRoles().clear();
        user.getRoles().add(role);
        if (!"GUEST".equals(roleCode)) {
            user.setStatus("ACTIVE");
        }
        AppUser saved = userRepo.saveAndFlush(user);
        auditService.saveAudit(id, "AppUser", "ASSIGN_ROLE", oldRole, roleCode);
        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse updateFullName(UUID id, String newName, String actorEmail) {
        AppUser user = userRepo.findById(id).orElseThrow(() -> new AdminException("User introuvable"));
        String oldName = user.getFullName();
        user.setFullName(newName);
        AppUser saved = userRepo.saveAndFlush(user);
        auditService.saveAudit(id, "AppUser", "UPDATE_NAME", oldName, newName);
        return UserResponse.from(saved);
    }

    @Transactional
    public void syncUser(UserSyncDTO dto) {
        try {
            log.info("📥 Réception synchro RabbitMQ pour : {}", dto.getEmail());

            AppUser user = userRepo.findByEmail(dto.getEmail()).orElseGet(() -> {
                AppUser newUser = new AppUser();
                UUID assignedId = dto.getId();
                if (assignedId == null) {
                    log.warn("⚠️ ID null reçu pour {}, génération d'un UUID local.", dto.getEmail());
                    assignedId = UUID.randomUUID();
                }
                newUser.setId(assignedId);
                newUser.setEmail(dto.getEmail());
                newUser.setCreatedAt(OffsetDateTime.now());
                newUser.setUpdatedAt(OffsetDateTime.now());
                newUser.setStatus("PENDING");
                return newUser;
            });

            user.setFullName((dto.getFullName() != null && !dto.getFullName().isBlank())
                    ? dto.getFullName()
                    : "Utilisateur_" + dto.getEmail().split("@")[0]);

            String roleCode = (dto.getRole() != null) ? dto.getRole() : "GUEST";
            user.setStatus(roleCode.equalsIgnoreCase("GUEST") ? "PENDING" : "ACTIVE");

            Role role = roleRepo.findByCode(roleCode)
                    .orElseThrow(() -> new AdminException("Rôle inconnu dans Admin-Service : " + roleCode));
            user.getRoles().clear();
            user.getRoles().add(role);

            userRepo.saveAndFlush(user);
            log.info("✅ Utilisateur {} synchronisé avec succès (Status: {})", user.getEmail(), user.getStatus());

            // ✅ Audit via auditService — transaction séparée REQUIRES_NEW
            auditService.saveAudit(user.getId(), "AppUser", "SYNC_RABBITMQ", "Synchronisation réussie", roleCode);

        } catch (Exception e) {
            log.error("❌ Erreur synchro : {}", e.getMessage());
            throw new RuntimeException("Erreur synchro : " + e.getMessage());
        }
    }

    @Transactional
    public void deleteByActorEmail(UUID id, String actorEmail) {
        AppUser user = userRepo.findById(id).orElseThrow(() -> new AdminException("User introuvable"));
        String userEmail = user.getEmail();
        userRepo.delete(user);
        userRepo.flush();
        auditService.saveAudit(id, "AppUser", "DELETE", userEmail, null);
    }
    // ❌ SUPPRIME toute la méthode saveAudit() ici
    @Transactional // Ajoute l'annotation pour assurer l'écriture en base
    public void updateStatusToRejected(String email) {
        // ✅ On utilise "userRepo" (la variable injectée) et non "AppUserRepository" (l'interface)
        userRepo.findByEmail(email).ifPresentOrElse(user -> {
            user.setStatus("REJECTED");
            user.setUpdatedAt(OffsetDateTime.now()); // Bonne pratique pour le suivi
            userRepo.saveAndFlush(user); // Force l'écriture immédiate
            log.info("✅ Utilisateur {} mis à jour en statut REJECTED dans admin-db", email);

            // ✅ Optionnel : Ajoute un audit pour ton PFE
            auditService.saveAudit(user.getId(), "AppUser", "ACCOUNT_REJECTED", "PENDING", "REJECTED");
        }, () -> {
            log.warn("⚠️ Impossible de rejeter l'utilisateur : {} non trouvé en base admin", email);
        });
    }
}