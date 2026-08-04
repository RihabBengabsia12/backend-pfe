package tn.rihab.authservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tn.rihab.authservice.security.JwtFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                // 1. Configuration CORS explicite pour Angular
                .cors(cors -> cors.configurationSource(request -> {
                    CorsConfiguration config = new CorsConfiguration();
                    config.setAllowedOrigins(List.of("http://localhost:4200"));
                    config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                    config.setAllowedHeaders(List.of("*"));
                    config.setAllowCredentials(true);
                    return config;
                }))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        // 2. Autoriser les requêtes de pré-vérification (Pre-flight)
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // 3. Routes Publiques (simplifiées)
                        .requestMatchers(
                                "/api/auth/login/**", "/auth/api/auth/login/**",
                                "/api/auth/register/**", "/auth/api/auth/register/**",
                                "/api/auth/health/**", "/auth/api/auth/health/**",
                                "/api/auth/refresh/**", "/auth/api/auth/refresh/**", // <--- AJOUTÉ ICI
                                "/api/auth/forgot-password/**", "/auth/api/auth/forgot-password/**",
                                "/api/auth/reset-password/**", "/auth/api/auth/reset-password/**"
                        ).permitAll()

                        // 4. Routes Admin : On utilise hasRole car le JwtFilter ajoute déjà "ROLE_"
                        .requestMatchers(
                                "/api/auth/validate-dossier/**", "/auth/api/auth/validate-dossier/**",
                                "/api/auth/activate-account/**", "/auth/api/auth/activate-account/**",
                                "/api/auth/reject/**", "/auth/api/auth/reject/**",
                                "/api/auth/accounts/**", "/auth/api/auth/accounts/**",
                                "/api/auth/events/**", "/auth/api/auth/events/**"
                        ).hasAnyAuthority("ROLE_ADMIN", "ADMIN")

                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}