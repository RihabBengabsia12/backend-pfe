package tn.rihab.adminservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.rihab.adminservice.entity.Permission;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    // JpaRepository contient déjà tout ce qu'il faut (findAll, findById, etc.)
}