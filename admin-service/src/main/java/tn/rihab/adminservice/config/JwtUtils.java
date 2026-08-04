package tn.rihab.adminservice.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

@Component
public class JwtUtils {

    // On retire la valeur par défaut ":" pour être sûre d'utiliser la clé du properties
    @Value("${jwt.secret}")
    private String secretKeyStr;

    private SecretKey getSigningKey() {
        // .trim() suffit pour nettoyer les extrémités
        String cleanKey = secretKeyStr.trim();
        byte[] keyBytes = cleanKey.getBytes(StandardCharsets.UTF_8);

        System.out.println("--- DIAGNOSTIC ADMIN-SERVICE ---");
        System.out.println("Taille binaire détectée : " + keyBytes.length + " bytes");

        // VALIDATION : HS512 nécessite au moins 64 octets (512 bits)
        // On ne force plus la taille exacte à 64, on accepte 64, 65, 66, etc.
        if (keyBytes.length < 64) {
            throw new IllegalStateException(
                    "ERREUR CONFIGURATION : La clé JWT est trop courte (" + keyBytes.length + " bytes). Minimum : 64."
            );
        }

        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (Exception e) {
            System.err.println("Admin-Service JWT Error: " + e.getMessage());
            return false;
        }
    }

    public Collection<? extends GrantedAuthority> extractAuthorities(String token) {
        try {
            Claims claims = extractAllClaims(token);
            // Correction de l'extraction du rôle pour être plus robuste
            Object role = claims.get("role");
            return (role != null) ? List.of(new SimpleGrantedAuthority(role.toString())) : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }
}