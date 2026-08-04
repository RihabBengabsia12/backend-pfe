package tn.rihab.adminservice.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.rihab.adminservice.entity.Permission;
import tn.rihab.adminservice.entity.Role;
import tn.rihab.adminservice.exception.AdminException;
import tn.rihab.adminservice.repository.PermissionRepository;
import tn.rihab.adminservice.repository.RoleRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepo;
    private final PermissionRepository permissionRepo;
    private final AuditService auditService; // ✅ remplace UserService

    public List<Map<String, Object>> findAllWithPermissions() {
        return roleRepo.findAll().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",    r.getId());
            m.put("code",  r.getCode());
            m.put("label", r.getLabel());
            m.put("permissions", r.getPermissions().stream()
                    .map(p -> Map.of("id", p.getId(), "code", p.getCode(), "label", p.getLabel()))
                    .collect(Collectors.toList()));
            return m;
        }).collect(Collectors.toList());
    }

    public List<UUID> getPermissionIds(UUID roleId) {
        Role role = roleRepo.findById(roleId)
                .orElseThrow(() -> new AdminException("Rôle introuvable : " + roleId));
        return role.getPermissions().stream()
                .map(Permission::getId)
                .collect(Collectors.toList());
    }

    @Transactional
    public void updateRolePermissions(UUID roleId, List<UUID> permissionIds) {
        Role role = roleRepo.findById(roleId)
                .orElseThrow(() -> new EntityNotFoundException("Rôle non trouvé"));

        List<Permission> permissions = permissionRepo.findAllById(permissionIds);
        role.setPermissions(new HashSet<>(permissions));
        roleRepo.save(role);

        // ✅ auditService au lieu de userService
        auditService.saveAudit(
                roleId,
                "ROLE",
                "UPDATE_PERMISSIONS",
                "Configuration précédente",
                "Nouvelle config : " + permissions.size() + " privilèges"
        );
    }

    @Transactional
    public void duplicateRole(UUID sourceRoleId, String newRoleLabel) {
        Role sourceRole = roleRepo.findById(sourceRoleId)
                .orElseThrow(() -> new EntityNotFoundException("Rôle source introuvable"));

        String newCode = newRoleLabel.trim().toUpperCase().replaceAll("\\s+", "_");

        Role newRole = Role.builder()
                .code(newCode)
                .label(newRoleLabel)
                .permissions(new HashSet<>(sourceRole.getPermissions()))
                .build();

        roleRepo.save(newRole);

        auditService.saveAudit(
                newRole.getId() != null ? newRole.getId() : UUID.randomUUID(), // Fallback
                "ROLE",
                "DUPLICATE_ROLE",
                "Dupliqué depuis : " + sourceRole.getCode(),
                "Création du profil : " + newRoleLabel + " (" + newRole.getPermissions().size() + " privilèges)"
        );
    }
}