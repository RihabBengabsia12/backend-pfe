package tn.rihab.analysteservice.dto.ia;



import lombok.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Contexte complet envoyé à ia-service pour générer les textes narratifs APO. */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApoGenerationContextDto {

    private UUID dossierId;

    // ── Contexte dossier (Phase 1) ─────────────────────────────────────────
    private String intituleOffre;
    private String client;
    private String pays;
    private String bailleurs;
    private String budgetGlobal;
    private String hommesMois;
    private String dtLimSoum;
    private String langue;

    // ── Scoring (Phase 2) ──────────────────────────────────────────────────
    private Double pwinScore;
    private String decisionAuto;
    private List<RisqueContexte> risques;

    // ── Matching (Phase 3) ─────────────────────────────────────────────────
    private String secteurAo;
    private Double tauxCouvertureCompetences;
    private Double tauxCouvertureExperts;
    private String gapRefs;
    private String matriceDiff;
    private Integer relationClientNiveau;

    // ── Contexte supplémentaire ────────────────────────────────────────────
    private String delaiGlobalMois;
    private String tjmImplicite;
    private String modeNotation;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RisqueContexte {
        private String nom;
        private String niveau;
        private String justification;
    }
}