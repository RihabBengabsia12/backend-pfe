package tn.rihab.projectservice.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AiLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "dossier_id", nullable = false)
    private UUID dossierId;

    @Column(name = "dossier_title")
    private String dossierTitle;

    @Column(name = "action_type", nullable = false)
    private String actionType;

    @Column(name = "model_used")
    private String modelUsed;

    @Column(name = "tokens_consumed")
    private Integer tokensConsumed;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "estimated_cost")
    private Double estimatedCost;

    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    @Column(name = "status")
    private String status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
