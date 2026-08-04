package tn.rihab.adminservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.*;

@Entity
@Table(name = "role")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;    // ADMIN, ANALYST, MANAGER

    @Column(nullable = false)
    private String label;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "role_permission",
            joinColumns = @JoinColumn(name = "role_id", columnDefinition = "UUID"),
            inverseJoinColumns = @JoinColumn(name = "permission_id", columnDefinition = "UUID")
    )
    private Set<Permission> permissions = new HashSet<>();
}