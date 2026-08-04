package tn.rihab.adminservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.rihab.adminservice.entity.AppUser;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByEmail(String email);
    boolean existsByEmail(String email);
}