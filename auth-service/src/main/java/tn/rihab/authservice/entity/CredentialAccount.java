package tn.rihab.authservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "credential_account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CredentialAccount {

    @Id
    @Column(name = "user_id") // On utilise userId comme clé primaire unique
    private UUID userId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "role")
    private String role;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "account_status", nullable = false)
    private String accountStatus;

    @Column(name = "failed_login_count", nullable = false)
    private Integer failedLoginCount;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "password_updated_at")
    private OffsetDateTime passwordUpdatedAt;

    @Column(name = "reset_token")
    private String resetToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        // Garantit que l'ID est généré s'il n'est pas fourni par l'Initializer
        if (userId == null) userId = UUID.randomUUID();

        if (failedLoginCount == null) failedLoginCount = 0;

        // Si l'admin initializer ne force pas le statut, on met PENDING
        if (accountStatus == null) {
            accountStatus = "PENDING";
        }

        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // ── Helpers métier ────────────────────────────────────
    public boolean isActive() { return "ACTIVE".equals(accountStatus); }
    public boolean isLocked() { return "LOCKED".equals(accountStatus); }

    public void incrementFailedLogin() {
        this.failedLoginCount++;
        if (this.failedLoginCount >= 5) this.accountStatus = "LOCKED";
    }

    public void resetFailedLogin() {
        this.failedLoginCount = 0;
        this.accountStatus = "ACTIVE";
        this.lastLoginAt = OffsetDateTime.now();
    }
}