package tn.rihab.adminservice.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.*;

@Entity
@Table(name = "app_user")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder

public class AppUser {

    @Id
    @JsonAlias({"userId", "id"})
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    // CORRECTION 1 : nullable = true temporairement ou gérer la valeur par défaut
    // Pour éviter l'erreur "violates not-null constraint"
    @Column(name = "full_name", nullable = true)
    private String fullName;

    @Column(nullable = false)
    @Builder.Default
    // CORRECTION 2 : On met PENDING par défaut ici aussi !
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = OffsetDateTime.now();
        }
        // CORRECTION 3 : Sécurité supplémentaire si le statut arrive vide
        if (status == null || status.equals("ACTIVE")) {
            status = "PENDING";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // ── Helpers Métier ─────────────────────────────────────
    public boolean isActive()   { return "ACTIVE".equals(status); }
    public boolean isPending()  { return "PENDING".equals(status); }
    public boolean isDisabled() { return "DISABLED".equals(status); }

    public String getRoleCode() {
        return roles.stream()
                .findFirst()
                .map(Role::getCode)
                .orElse("GUEST");
    }

    public Set<String> getPermissionCodes() {
        Set<String> codes = new HashSet<>();
        roles.forEach(r -> r.getPermissions()
                .forEach(p -> codes.add(p.getCode())));
        return codes;
    }
}