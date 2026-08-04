package tn.rihab.adminservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tn.rihab.adminservice.entity.Permission;
import tn.rihab.adminservice.repository.PermissionRepository;

import java.util.List;

@RestController
@RequestMapping("/api/admin/permissions") // Cette URL est attendue par Angular
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PermissionController {
    private final PermissionRepository permissionRepo;
    @GetMapping
    public List<Permission> getAll() {
        return permissionRepo.findAll(); // C'est ici que le Front récupère vos 12 permissions
    }
}