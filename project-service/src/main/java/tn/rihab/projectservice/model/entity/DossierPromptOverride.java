package tn.rihab.projectservice.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "dossier_prompt_overrides")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DossierPromptOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id", nullable = false)
    private Dossier dossier;

    @Column(name = "prompt_filename", nullable = false)
    private String promptFilename;

    @Column(name = "custom_content", columnDefinition = "TEXT", nullable = false)
    private String customContent;
}
