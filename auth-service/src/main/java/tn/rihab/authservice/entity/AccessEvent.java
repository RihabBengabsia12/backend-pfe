package tn.rihab.authservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "access_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccessEvent {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "email_attempted")
    private String emailAttempted;

    @Column(name = "event_type", nullable = false)
    private String eventType;   // LOGIN_SUCCESS, LOGIN_FAILURE, LOGOUT, TOKEN_REFRESH

    @Column(nullable = false)
    private String result;      // SUCCESS, FAILURE

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        occurredAt = OffsetDateTime.now();
    }

    // ── Factory methods ───────────────────────────────────
    public static AccessEvent success(UUID userId, String email,
                                      String eventType, String ip, String ua) {
        return AccessEvent.builder()
                .userId(userId).emailAttempted(email)
                .eventType(eventType).result("SUCCESS")
                .ipAddress(ip).userAgent(ua).build();
    }

    public static AccessEvent failure(String email, String eventType,
                                      String ip, String ua) {
        return AccessEvent.builder()
                .emailAttempted(email)
                .eventType(eventType).result("FAILURE")
                .ipAddress(ip).userAgent(ua).build();
    }
}