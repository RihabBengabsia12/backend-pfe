package tn.rihab.analysteservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.rihab.analysteservice.client.IaServiceClient;
import tn.rihab.analysteservice.client.ProjectServiceClient;
import tn.rihab.analysteservice.dto.DossierDto;
import tn.rihab.analysteservice.dto.ia.ExtractionResponseDto;
import tn.rihab.analysteservice.dto.ia.ChampResultDto;
import tn.rihab.analysteservice.dto.ia.RiskAnalysisResponseDto;
import tn.rihab.analysteservice.matching.MatchingEngine;
import tn.rihab.analysteservice.messaging.AnalysteEventPublisher;
import tn.rihab.analysteservice.model.AnalyseDossier;
import tn.rihab.analysteservice.model.MatchingResult;
import tn.rihab.analysteservice.model.PwinScore;
import tn.rihab.analysteservice.repository.AnalyseDossierRepository;
import tn.rihab.analysteservice.repository.IaAuditLogRepository;
import tn.rihab.analysteservice.repository.PwinScoreRepository;
import tn.rihab.analysteservice.scoring.ScoringEngine;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
class AnalyseDeepServiceTest {

    @Mock private ProjectServiceClient projectClient;
    @Mock private IaServiceClient iaClient;
    @Mock private ScoringEngine scoringEngine;
    @Mock private MatchingEngine matchingEngine;
    @Mock private ApoAssemblyService apoAssemblyService;
    @Mock private NoGoReportService noGoReportService;
    @Mock private AnalysteEventPublisher publisher;
    @Mock private AnalyseDossierRepository analyseRepo;
    @Mock private PwinScoreRepository pwinRepo;
    @Mock private IaAuditLogRepository auditLogRepo;
    @Mock private SseService sseService;

    @InjectMocks
    private AnalyseDeepService analyseDeepService;

    private UUID dossierId;
    private DossierDto dossier;
    private String documentText;

    @BeforeEach
    void setUp() {
        dossierId = UUID.randomUUID();
        dossier = new DossierDto();
        dossier.setId(dossierId);
        documentText = "Ceci est un document de test de 100 pages...";

        lenient().when(projectClient.getDossier(dossierId)).thenReturn(dossier);
        lenient().when(projectClient.getDocumentText(dossierId)).thenReturn(documentText);
        lenient().when(analyseRepo.findByDossierId(dossierId)).thenReturn(Optional.empty());
        lenient().when(analyseRepo.save(any(AnalyseDossier.class))).thenAnswer(i -> i.getArgument(0));
    }

    // --- CAS SENSIBLE : TF-21 - Parcours complet (E2E) GO ---
    @Test
    void TF_21_01_Parcours_Complet_E2E_GO() {
        // 1. Mock Extraction Phase 2
        ExtractionResponseDto p2 = new ExtractionResponseDto();
        p2.setChamps(Map.of("NOTE_MINIMALE", new ChampResultDto("75", 0.9, "Source")));
        lenient().when(iaClient.extractPhase2(any())).thenReturn(p2);

        // 2. Mock Extraction Risques
        RiskAnalysisResponseDto risks = new RiskAnalysisResponseDto();
        risks.setRisques(Map.of("RISQUES_FINANCIERS", new tn.rihab.analysteservice.dto.ia.RiskItemDto("Modéré", "Ok")));
        lenient().when(iaClient.extractRisks(any())).thenReturn(risks);

        // 3. Mock Scoring (Donne un GO)
        PwinScore scoreGo = new PwinScore();
        scoreGo.setScoreGlobal(85.0);
        scoreGo.setDecisionAuto("GO");
        lenient().when(scoringEngine.calculate(any(), any(), any())).thenReturn(scoreGo);

        // 4. Mock Matching
        MatchingResult matching = new MatchingResult();
        matching.setTauxCouvertureCompetences(0.9);
        lenient().when(matchingEngine.runMatching(any(), anyString())).thenReturn(matching);

        // Appel
        analyseDeepService.runFullPipeline(dossierId);

        // Vérifications (Le pipeline complet a tourné)
        verify(iaClient, times(1)).extractPhase2(any());
        verify(iaClient, times(1)).extractRisks(any());
        verify(scoringEngine, atLeastOnce()).calculate(any(), any(), any());
        verify(publisher, times(1)).publishScoringCompleted(eq(dossierId), anyString(), any(), eq(85.0), eq("GO"), any());
        verify(matchingEngine, times(1)).runMatching(any(), anyString());
        
        // Le rapport No-Go ne doit PAS avoir été appelé
        verify(noGoReportService, never()).generate(any(), any(), any(), any(), anyString());
    }

    // --- CAS SENSIBLE : TF-21 - Parcours complet (E2E) NO-GO ---
    @Test
    void TF_21_02_Parcours_Complet_E2E_NOGO() {
        // 1. Mock Extraction Phase 2
        ExtractionResponseDto p2 = new ExtractionResponseDto();
        p2.setChamps(Map.of("NOTE_MINIMALE", new ChampResultDto("75", 0.9, "Source")));
        lenient().when(iaClient.extractPhase2(any())).thenReturn(p2);

        // 2. Mock Extraction Risques
        RiskAnalysisResponseDto risks = new RiskAnalysisResponseDto();
        risks.setRisques(Map.of("RISQUES_FINANCIERS", new tn.rihab.analysteservice.dto.ia.RiskItemDto("Rédhibitoire", "Critique")));
        lenient().when(iaClient.extractRisks(any())).thenReturn(risks);

        // 3. Mock Scoring (Donne un NO_GO)
        PwinScore scoreNoGo = new PwinScore();
        scoreNoGo.setScoreGlobal(20.0);
        scoreNoGo.setDecisionAuto("NO_GO");
        lenient().when(scoringEngine.calculate(any(), any(), any())).thenReturn(scoreNoGo);

        // Appel
        analyseDeepService.runFullPipeline(dossierId);

        // Vérifications
        verify(iaClient, times(1)).extractPhase2(any());
        verify(iaClient, times(1)).extractRisks(any());
        
        // Le rapport No-Go DOIT avoir été généré
        verify(noGoReportService, times(1)).generate(eq(dossierId), any(), any(), any(), anyString());
        
        // Le Matching NE DOIT PAS avoir été appelé car le pipeline s'arrête
        verify(matchingEngine, never()).runMatching(any(), anyString());
    }
}
