package tn.rihab.analysteservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ia_audit_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IaAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "dossier_id", nullable = false)
    private UUID dossierId;

    @Column(name = "action_name", nullable = false)
    private String actionName;

    @Column(name = "token_usage")
    private Integer tokenUsage;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "cache_creation_tokens")
    private Integer cacheCreationTokens;

    @Column(name = "cache_read_tokens")
    private Integer cacheReadTokens;

    @Column(name = "processing_time_ms")
    private Integer processingTimeMs;

    @Column(name = "estimated_cost")
    private Double estimatedCost;

    @Column(name = "raw_json", columnDefinition = "TEXT")
    private String rawJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
