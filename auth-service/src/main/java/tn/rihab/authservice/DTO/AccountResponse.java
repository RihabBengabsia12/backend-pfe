package tn.rihab.authservice.DTO;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

// ── Réponse GET /me ───────────────────────────────────────
@Data
@Builder
@NoArgsConstructor  // <--- AJOUTÉ : Nécessaire pour la désérialisation JSON
@AllArgsConstructor // <--- AJOUTÉ : Nécessaire pour le @Builder
public class AccountResponse {
    private UUID   id;
    private String email;
    private String accountStatus;
    private String role;
    private Integer failedLoginCount;
    private OffsetDateTime lastLoginAt;
    private OffsetDateTime createdAt;
}