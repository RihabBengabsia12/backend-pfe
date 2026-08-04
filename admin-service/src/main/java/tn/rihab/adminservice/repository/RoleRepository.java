package tn.rihab.adminservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.rihab.adminservice.entity.Role;
import java.util.Optional;import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
    Optional<Role> findByCode(String code);

}
