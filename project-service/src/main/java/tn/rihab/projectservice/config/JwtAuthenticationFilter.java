package tn.rihab.projectservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j // Utilise Slf4j au lieu de System.out.println pour ton rapport de PFE
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;

    @Value("${projectiq.internal-api-key:}")
    private String internalApiKey;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // Ignorer les requêtes vers le health check ou actuator si nécessaire
        if (request.getServletPath().contains("/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        String serviceKey = request.getHeader("X-Internal-Service-Key");
        if (internalApiKey != null && !internalApiKey.isBlank()
                && internalApiKey.equals(serviceKey)) {
            UsernamePasswordAuthenticationToken internalAuth =
                    new UsernamePasswordAuthenticationToken("analyste-service", null,
                            java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_INTERNAL")));
            SecurityContextHolder.getContext().setAuthentication(internalAuth);
            filterChain.doFilter(request, response);
            return;
        }

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        try {
            final String userEmail = jwtUtils.extractUsername(jwt);

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                if (jwtUtils.isTokenValid(jwt)) {
                    // On extrait proprement les autorités (ADMIN, etc.)
                    var authorities = jwtUtils.extractAuthorities(jwt);

                    log.info("Authentification réussie pour : {} avec les rôles : {}", userEmail, authorities);

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userEmail,
                            null,
                            authorities
                    );

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } else {
                    log.warn("Tentative de connexion avec un token invalide ou expiré.");
                }
            }
        } catch (Exception e) {
            log.error("Erreur lors de la validation du JWT : {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
