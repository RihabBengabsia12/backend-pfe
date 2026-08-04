package tn.rihab.analysteservice.model;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.UUID;

/**
 * Référentiel des compétences sectorielles d'Egis.
 * Utilisé par CompetencesMatcher pour calculer le taux de couverture
 * face aux exigences techniques extraites de la DP.
 */
@Entity
@Table(name = "competences")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Competence {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Ex: "Eau & Assainissement", "Transport", "Énergie" */
    @Column(nullable = false, length = 100)
    private String domaine;

    /** Ex: "Hydraulique urbaine", "BRT", "Énergies renouvelables" */
    @Column(length = 150)
    private String sousDomaine;

    /** DEBUTANT | CONFIRME | EXPERT | REFERENCE */
    @Column(length = 20)
    private String niveau;

    /** Mots-clés pour matching textuel avec la DP */
    @ElementCollection
    @CollectionTable(name = "competence_mots_cles", joinColumns = @JoinColumn(name = "competence_id"))
    @Column(name = "mot_cle")
    private List<String> motsCles;

    @Column @Builder.Default
    private Boolean actif = true;
}