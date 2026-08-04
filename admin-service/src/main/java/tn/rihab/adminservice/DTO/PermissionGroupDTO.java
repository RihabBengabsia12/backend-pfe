package tn.rihab.adminservice.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import tn.rihab.adminservice.entity.Permission;

import java.util.List;

@Data
@AllArgsConstructor
public class PermissionGroupDTO {
    private String sectionName; // Exemple: "Analyse des Dossiers"
    private List<Permission> permissions;
}
