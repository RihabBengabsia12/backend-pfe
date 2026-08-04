package tn.rihab.authservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import tn.rihab.authservice.entity.CredentialAccount;
import tn.rihab.authservice.repository.AccountRepository; // Vérifie ton nom de package

import java.time.OffsetDateTime;
import java.util.UUID;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class AdminInitializer {

    private final AccountRepository accountRepo;
    private final PasswordEncoder encoder;

    @Bean
    CommandLineRunner initRootAdmin() {
        return args -> {
            String adminEmail = "admin@st2i.tn";

            // Vérifier si l'admin existe déjà pour ne pas le recréer à chaque redémarrage
            if (accountRepo.findByEmail(adminEmail).isEmpty()) {
                log.info(">>>> [INIT] Aucun administrateur système trouvé. Initialisation du Niveau 0...");

                CredentialAccount rootAdmin = new CredentialAccount();
                rootAdmin.setUserId(UUID.randomUUID());
                rootAdmin.setEmail(adminEmail);

                // Mot de passe par défaut : admin123
                rootAdmin.setPasswordHash(encoder.encode("admin123"));

                // On force le rôle ADMIN (Niveau 0)
                rootAdmin.setRole("ADMIN");

                rootAdmin.setAccountStatus("ACTIVE");
                rootAdmin.setCreatedAt(OffsetDateTime.now());
                rootAdmin.setFailedLoginCount(0);

                accountRepo.save(rootAdmin);

                log.info(">>>> [INIT] Compte Niveau 0 créé avec succès !");
                log.info(">>>> [INIT] Email : {} | Password : admin123", adminEmail);
            } else {
                log.info(">>>> [INIT] L'administrateur système '{}' existe déjà. Saut de l'initialisation.", adminEmail);
            }
        };
    }
}