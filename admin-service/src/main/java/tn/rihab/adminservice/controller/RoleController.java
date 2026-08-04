package tn.rihab.adminservice.controller;

import lombok.RequiredArgsConstructor;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tn.rihab.adminservice.service.RoleService;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getRoles() {
        return ResponseEntity.ok(roleService.findAllWithPermissions());
    }

    // --- CORRECTION ICI ---
    // On change List<String> en List<UUID> et on appelle getPermissionIds
    @GetMapping("/{id}/permissions")
    public ResponseEntity<List<UUID>> getPermissions(@PathVariable UUID id) {
        return ResponseEntity.ok(roleService.getPermissionIds(id));
    }

    @PutMapping("/{id}/permissions")
    public ResponseEntity<Void> updatePermissions(
            @PathVariable UUID id,
            @RequestBody Map<String, List<UUID>> request) {

        List<UUID> permissionIds = request.get("permissionIds");
        roleService.updateRolePermissions(id, permissionIds);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<Void> duplicateRole(@PathVariable UUID id, @RequestParam String newName) {
        roleService.duplicateRole(id, newName);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportRolesConfig() {
        try {
            List<Map<String, Object>> rolesConfig = roleService.findAllWithPermissions();
            ObjectMapper mapper = new ObjectMapper();
            byte[] jsonBytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(rolesConfig);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=security_roles_config.json")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}