package tn.rihab.projectservice.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "anonymization_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnonymizationToken {

    @Id
    @Column(name = "token", length = 100, nullable = false, updatable = false)
    private String token;

    @Column(name = "original_value", length = 1000, nullable = false, updatable = false)
    private String originalValue;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
