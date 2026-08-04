package tn.rihab.adminservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "data_event")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DataEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID id;

    // --- L'ACTEUR (DÉTAILLÉ POUR ANGULAR) ---
    @Column(name = "actor_email")
    private String actorEmail; // ex: rihab@pfe.tn

    @Column(name = "actor_name")
    private String actorName;  // ex: Rihab (Admin)

    @Column(name = "actor_user_id", nullable = true) // Changé en nullable
    private UUID actorUserId;

    // --- L'OBJET MODIFIÉ ---
    @Column(name = "entity_name", nullable = false)
    private String entityName;

    @Column(name = "entity_id", nullable = false)
    private String entityId;

    @Column(name = "ressource", nullable = false)
    private String ressource;

    // --- L'ACTION ---
    @Column(nullable = false)
    private String action;

    @Column(name = "old_data", length = 1000) // length pour les longs JSON
    private String oldData;

    @Column(name = "new_data", length = 1000)
    private String newData;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;



    @PrePersist
    protected void onCreate() {
        if (occurredAt == null) {
            occurredAt = OffsetDateTime.now();
        }
        // Sécurité : si ressource est vide, on met le nom de l'entité
        if (ressource == null) {
            ressource = entityName;
        }
    }
}