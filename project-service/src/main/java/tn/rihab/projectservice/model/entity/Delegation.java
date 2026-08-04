package tn.rihab.projectservice.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "delegation")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Delegation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String roleName; // e.g., "DO", "DDA", "DGA", "PDG"

    @Column(nullable = false, length = 100)
    private String displayName; // e.g., "Direction de l'Offre"

    @Column(nullable = false, length = 100)
    private String email;

    @Column
    private Double threshold; // can be null for DO
}
