package tn.rihab.projectservice.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

import java.util.UUID;

@Entity
@Table(name = "anonymization_dict")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AnonymizationDict {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "original_word", nullable = false, unique = true, length = 100)
    private String originalWord;

    @Column(name = "replacement_code", length = 50)
    private String replacementCode;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
