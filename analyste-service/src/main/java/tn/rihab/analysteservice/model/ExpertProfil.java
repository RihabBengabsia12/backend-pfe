package tn.rihab.analysteservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Référentiel des experts mobilisables par Egis.
 * Utilisé par ExpertsMatcher pour calculer [[TAUX_COUVERTURE_EXPERTS]] :
 * compare [[EXPERTS_REQUIS]] (extrait du TDR) avec les profils
 * disponibles sur la période de la mission.
 */
@Entity
@Table(name = "expert_profils")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExpertProfil {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String nom;

    /** Domaines d'expertise — doit matcher Competence.domaine / sousDomaine */
    @ElementCollection
    @CollectionTable(name = "expert_specialites", joinColumns = @JoinColumn(name = "expert_id"))
    @Column(name = "specialite")
    private List<String> specialites;

    @ElementCollection
    @CollectionTable(name = "expert_langues", joinColumns = @JoinColumn(name = "expert_id"))
    @Column(name = "langue")
    private List<String> langues;

    @Column(name = "annees_experience")
    private Integer anneesExperience;

    @Column(name = "disponible_du")
    private LocalDate disponibleDu;

    @Column(name = "disponible_au")
    private LocalDate disponibleAu;

    /** Taux journalier moyen en euros — utilisé pour estimer le budget réel */
    @Column(name = "taux_journalier")
    private Double tauxJournalier;

    @Column @Builder.Default
    private Boolean actif = true;
}