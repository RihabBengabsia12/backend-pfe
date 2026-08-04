package tn.rihab.authservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tn.rihab.authservice.entity.CredentialAccount;
import java.util.Optional;
import java.util.UUID;


@Repository
// J'ai supprimé l'import inutile de AccountRepository ici
public interface AccountRepository extends JpaRepository<CredentialAccount, UUID> {

    // Cette méthode est utilisée par l'AdminInitializer pour vérifier si l'admin existe
    Optional<CredentialAccount> findByEmail(String email);
}