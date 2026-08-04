package tn.rihab.authservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.rihab.authservice.entity.CredentialAccount;
import java.util.Optional;
import java.util.UUID;


public interface CredentialAccountRepository extends JpaRepository<CredentialAccount, UUID> {
    Optional<CredentialAccount> findByEmail(String email);
    Optional<CredentialAccount> findByUserId(UUID userId);
    Optional<CredentialAccount> findByResetToken(String resetToken);
}