package tn.rihab.analysteservice.model;


import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Référentiel des projets passés d'Egis.
 * Utilisé par ReferencesMatcher pour calculer [[GAP_REFS]] :
 * compare les exigences de références de la DP avec ce référentiel
 * (secteur, pays/zone, taille budgétaire, ancienneté).
 */
@Entity
@Table(name = "references_projets")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Reference {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 500)
    private String titre;

    @Column(length = 300)
    private String client;

    @Column(length = 100)
    private String pays;

    /** Doit correspondre aux valeurs de Competence.domaine pour le matching */
    @Column(length = 100)
    private String secteur;

    @Column(length = 200)
    private String bailleur;

    private Integer annee;

    @Column(name = "duree_mois")
    private Integer dureeMois;

    @Column(name = "budget_euros")
    private Double budgetEuros;

    @Column(name = "hommes_mois")
    private Double hommesMois;

    @Column(name = "description_courte", length = 1000)
    private String descriptionCourte;

    @Column @Builder.Default
    private Boolean actif = true;
}