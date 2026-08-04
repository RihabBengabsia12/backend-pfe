package tn.rihab.adminservice.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

public class SecurityUtils {

    /**
     * Extrait l'email de l'utilisateur connecté depuis le contexte de sécurité.
     * Dans notre JwtAuthenticationFilter, le principal est une String (l'email).
     */
    public static String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated()) {
            Object principal = auth.getPrincipal();

            // C'est ici que ton code récupère l'email envoyé par le filtre
            if (principal instanceof String email) {
                return email;
            }
        }

        // Si on arrive ici, c'est que personne n'est connecté
        throw new AuthenticationCredentialsNotFoundException("Utilisateur non trouvé dans le contexte de sécurité");
    }

}