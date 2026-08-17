package tn.rihab.analysteservice.scoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.rihab.analysteservice.dto.DossierDto;
import tn.rihab.analysteservice.model.AnalyseDossier;
import tn.rihab.analysteservice.model.PwinScore;
import tn.rihab.analysteservice.model.ScoringConfig;
import tn.rihab.analysteservice.repository.PwinScoreRepository;
import tn.rihab.analysteservice.scoring.calculators.AxeCalculator;
import tn.rihab.analysteservice.scoring.calculators.AxeRisquesCalculator;
import tn.rihab.analysteservice.scoring.calculators.AxeRisquesCalculator.RisquesResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
class ScoringEngineTest {

    @Mock
    private AxeRisquesCalculator axeRisquesCalculator;
    @Mock
    private ScoringConfigService scoringConfigService;
    @Mock
    private PwinScoreRepository pwinRepo;
    @Mock
    private AxeCalculator mockAxeA;

    private ScoringEngine scoringEngine;
    private DossierDto dossierDto;
    private AnalyseDossier analyseDossier;

    @BeforeEach
    void setUp() {
        // Préparer un calculateur mocké qui représente tout le reste du score
        lenient().when(mockAxeA.getAxeCode()).thenReturn("AXE_A");
        lenient().when(mockAxeA.getPoids(any())).thenReturn(1.0); // Poids total de 100% pour simplifier le calcul

        // Injection manuelle car on a une List de beans
        scoringEngine = new ScoringEngine(
                List.of(mockAxeA),
                axeRisquesCalculator,
                scoringConfigService,
                pwinRepo
        );

        dossierDto = new DossierDto();
        dossierDto.setId(UUID.randomUUID());

        analyseDossier = new AnalyseDossier();
        analyseDossier.setDossierId(dossierDto.getId());

        when(pwinRepo.findByDossierId(any())).thenReturn(Optional.empty());
        when(pwinRepo.save(any(PwinScore.class))).thenAnswer(i -> i.getArgument(0));
    }

    // --- CAS SENSIBLE : TF-13 - Calcul P-Win favorable (GO) ---
    @Test
    void TF_13_01_CalculPWin_Favorable_GO() {
        // Mock : Pas de risque rédhibitoire
        when(axeRisquesCalculator.calculate(any())).thenReturn(new RisquesResult(0.9, false, null));
        // Mock : L'axe A renvoie un excellent score de 95% (0.95)
        when(mockAxeA.calculate(any(), any(), any(), any())).thenReturn(0.95);

        // Appel
        PwinScore result = scoringEngine.calculate(dossierDto, analyseDossier, null);

        // Vérifications
        assertNotNull(result);
        assertEquals(95.0, result.getScoreGlobal(), "Le score global doit être de 95%");
        assertEquals("GO", result.getDecisionAuto(), "La décision doit être automatique GO");
        assertFalse(Boolean.TRUE.equals(result.getRisqueRedhibitoire()), "Il ne doit pas y avoir de risque rédhibitoire");
        assertNull(result.getMotifNogo(), "Le motif de refus doit être nul");
    }

    // --- CAS SENSIBLE : TF-14 - Calcul P-Win Risque Rédhibitoire (NO_GO) ---
    @Test
    void TF_14_01_CalculPWin_RisqueRedhibitoire_NOGO() {
        // Mock : Un risque FATAL (ex: Pays sous embargo)
        when(axeRisquesCalculator.calculate(any())).thenReturn(new RisquesResult(0.0, true, "Pays sous sanction internationale (Embargo)"));
        
        // Même si les autres critères sont parfaits, le risque rédhibitoire doit écraser le score
        when(mockAxeA.calculate(any(), any(), any(), any())).thenReturn(1.0);

        // Appel
        PwinScore result = scoringEngine.calculate(dossierDto, analyseDossier, null);

        // Vérifications
        assertNotNull(result);
        assertEquals(0.0, result.getScoreGlobal(), "Le score global doit être de 0% à cause du risque rédhibitoire");
        assertEquals("NO_GO", result.getDecisionAuto(), "La décision doit être NO_GO direct");
        assertTrue(Boolean.TRUE.equals(result.getRisqueRedhibitoire()), "Le drapeau de risque rédhibitoire doit être levé");
        assertTrue(result.getMotifNogo().contains("Embargo"), "Le motif doit mentionner l'embargo");
    }

    // --- CAS SENSIBLE : TF-14 - Calcul P-Win Score Faible (MANUAL / NO_GO) ---
    @Test
    void TF_14_02_CalculPWin_ScoreFaible_MANUAL() {
        // Mock : Pas de risque rédhibitoire grave
        when(axeRisquesCalculator.calculate(any())).thenReturn(new RisquesResult(0.5, false, null));
        // Mock : Score global très moyen (ex: 45%)
        when(mockAxeA.calculate(any(), any(), any(), any())).thenReturn(0.45);
        when(mockAxeA.getLabelErreur()).thenReturn("Score insuffisant sur l'Axe A");

        // Appel
        PwinScore result = scoringEngine.calculate(dossierDto, analyseDossier, null);

        // Vérifications
        assertNotNull(result);
        assertEquals(45.0, result.getScoreGlobal(), "Le score global doit être de 45%");
        // En dessous de 50 (seuil par défaut), la décision est souvent MANUAL ou NO_GO selon la config de secours
        assertEquals("MANUAL", result.getDecisionAuto(), "La décision doit nécessiter une validation MANUELLE");
        assertFalse(Boolean.TRUE.equals(result.getRisqueRedhibitoire()));
        assertNotNull(result.getMotifNogo());
        assertTrue(result.getMotifNogo().contains("Score insuffisant"), "Le motif doit expliquer pourquoi c'est faible");
    }

    // --- TEST NON-FONCTIONNEL : TNF-09 - Maintenabilité (changement paramétrage dynamique) ---
    @Test
    void TNF_09_01_Changement_Dynamique_Parametrage() {
        // ÉTAPE 1 : Configuration initiale stricte (seuil GO à 80%)
        ScoringConfig configStricte = new ScoringConfig();
        configStricte.setSeuilNoGo(40.0);
        configStricte.setSeuilGoConditionnel(60.0);
        configStricte.setSeuilGoFort(80.0);
        
        when(scoringConfigService.getCurrent()).thenReturn(configStricte);
        when(axeRisquesCalculator.calculate(any())).thenReturn(new RisquesResult(0.9, false, null));
        when(mockAxeA.calculate(any(), any(), any(), any())).thenReturn(0.75); // Score = 75%

        // Premier calcul avec config stricte -> Le score 75% est < 80% donc GO_CONDITIONNEL
        PwinScore result1 = scoringEngine.calculate(dossierDto, analyseDossier, null);
        assertEquals(75.0, result1.getScoreGlobal());
        assertEquals("GO_CONDITIONNEL", result1.getDecisionAuto());

        // ÉTAPE 2 : L'administrateur change la configuration à chaud (sans redémarrer)
        ScoringConfig configSouple = new ScoringConfig();
        configSouple.setSeuilNoGo(30.0);
        configSouple.setSeuilGoConditionnel(50.0);
        configSouple.setSeuilGoFort(70.0); // Seuil GO abaissé à 70%

        when(scoringConfigService.getCurrent()).thenReturn(configSouple);

        // Deuxième calcul avec la NOUVELLE config souple -> Le score 75% est >= 70% donc GO direct
        PwinScore result2 = scoringEngine.calculate(dossierDto, analyseDossier, null);
        assertEquals(75.0, result2.getScoreGlobal());
        assertEquals("GO", result2.getDecisionAuto(), "Le moteur doit appliquer la nouvelle configuration immédiatement sans redémarrage");
    }
}
