package tn.rihab.authservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private static final Logger logger = LoggerFactory.getLogger(JwtFilter.class);


    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        final String requestURI = request.getRequestURI();

        // ✅ LOG 1 : Voir si la requête arrive même au filtre
        System.out.println("🚀 [DEBUG SECURITY] URI demandée : " + requestURI);

        if (isPublicRoute(requestURI)) {
            System.out.println("🔓 [DEBUG SECURITY] Route publique détectée : " + requestURI);
            filterChain.doFilter(request, response);
            return;
        }

        final String authHeader = request.getHeader("Authorization");

        // ✅ LOG 2 : Voir si Angular envoie bien le Token
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            System.err.println("❌ [DEBUG SECURITY] AUCUN TOKEN trouvé dans le header pour : " + requestURI);
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        try {
            if (jwtService.isValid(jwt)) {
                String email = jwtService.extractEmail(jwt);
                String role = jwtService.extractRole(jwt);

                // ✅ LOG 3 : Voir ce qu'il y a RÉELLEMENT dans le token
                System.out.println("💎 [DEBUG SECURITY] Token déchiffré ! Email: " + email + " | Role brut: " + role);

                if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    String roleWithPrefix = (role.startsWith("ROLE_")) ? role : "ROLE_" + role;
                    SimpleGrantedAuthority authority = new SimpleGrantedAuthority(roleWithPrefix);

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            email, null, Collections.singletonList(authority)
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // Injection dans le contexte
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    // ✅ LOG 4 : Vérification finale du contexte Spring
                    System.out.println("✅ [DEBUG SECURITY] Contexte mis à jour ! Autorité injectée : " +
                            SecurityContextHolder.getContext().getAuthentication().getAuthorities());

                    request.setAttribute("email", email);
                }
            } else {
                System.err.println("❌ [DEBUG SECURITY] Le token est expiré ou corrompu !");
            }
        } catch (Exception e) {
            System.err.println("🔥 [DEBUG SECURITY] Erreur fatale dans le filtre : " + e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPublicRoute(String uri) {
        // Garde UNIQUEMENT ce qui est accessible sans être connecté
        return uri.contains("/api/auth/login")
                || uri.contains("/api/auth/register")
                || uri.contains("/api/auth/refresh")
                || uri.contains("/api/auth/forgot-password")
                || uri.contains("/api/auth/reset-password")
                || uri.contains("/api/auth/health");
    }
}
