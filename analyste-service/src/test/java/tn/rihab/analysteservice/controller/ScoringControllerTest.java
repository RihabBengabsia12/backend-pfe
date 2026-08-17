package tn.rihab.analysteservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import tn.rihab.analysteservice.client.ProjectServiceClient;
import tn.rihab.analysteservice.model.PwinScore;
import tn.rihab.analysteservice.repository.AnalyseDossierRepository;
import tn.rihab.analysteservice.repository.PwinScoreRepository;
import tn.rihab.analysteservice.scoring.ScoringEngine;
import tn.rihab.analysteservice.service.NoGoReportService;
import tn.rihab.analysteservice.service.SseService;
import tn.rihab.analysteservice.model.AnalyseDossier;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
class ScoringControllerTest {

    @Mock private ScoringEngine scoringEngine;
    @Mock private PwinScoreRepository pwinRepo;
    @Mock private ProjectServiceClient projectClient;
    @Mock private AnalyseDossierRepository analyseRepo;
    @Mock private NoGoReportService noGoReportService;
    @Mock private SseService sseService;

    @InjectMocks
    private ScoringController scoringController;

    private UUID dossierId;
    private PwinScore pwinScore;

    @BeforeEach
    void setUp() {
        dossierId = UUID.randomUUID();
        pwinScore = new PwinScore();
        pwinScore.setDossierId(dossierId);
        pwinScore.setScoreGlobal(45.0);
        pwinScore.setDecisionAuto("MANUAL");
        pwinScore.setRisqueRedhibitoire(false);
    }

    // --- CAS SENSIBLE : TF-17 - Refus Force-Go (Justification trop courte) ---
    @Test
    void TF_17_01_Refus_ForceGo_Justification_Courte() {
        // Justification de moins de 50 mots
        Map<String, String> request = Map.of(
                "justification", "Ceci est une justification trop courte, le client est important.",
                "type", "STRATEGIQUE",
                "forcedBy", "Directeur Général"
        );

        // Appel & Vérification de l'Exception
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            scoringController.forceGo(dossierId, request);
        });

        // La règle exige au moins 50 mots
        assertTrue(exception.getMessage().contains("au moins 50 mots"), "Le message doit mentionner la limite de 50 mots");
    }

    // --- CAS SENSIBLE : TF-18 - Acceptation Force-Go (Justification Valide) ---
    @Test
    void TF_18_01_Acceptation_ForceGo_Justification_Valide() {
        when(pwinRepo.findByDossierId(dossierId)).thenReturn(Optional.of(pwinScore));
        when(pwinRepo.save(any(PwinScore.class))).thenAnswer(i -> i.getArgument(0));

        // Justification de plus de 50 mots (on en génère une longue pour le test)
        StringBuilder justifLongue = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            justifLongue.append("mot").append(i).append(" ");
        }

        Map<String, String> request = Map.of(
                "justification", justifLongue.toString(),
                "type", "STRATEGIQUE",
                "forcedBy", "Directeur Général"
        );

        // Appel
        ResponseEntity<Map<String, Object>> response = scoringController.forceGo(dossierId, request);

        // Vérifications
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(pwinScore.getForceGo(), "Le drapeau forceGo doit être levé");
        assertEquals("STRATEGIQUE", pwinScore.getForceGoType());
        assertNotNull(pwinScore.getForceGoAt(), "La date de forçage doit être enregistrée");
        
        // S'assurer que le rapport No-Go est bien régénéré et l'analyste notifié
        verify(pwinRepo, times(1)).save(pwinScore);
        verify(projectClient, times(1)).notifyAnalyst(dossierId);
    }

    // --- CAS SENSIBLE : TF-19 - Validation finale No-Go ---
    @Test
    void TF_19_01_Validation_Finale_NoGo() {
        // Initialisation mock Dossier et Analyse
        tn.rihab.analysteservice.dto.DossierDto dossierDto = new tn.rihab.analysteservice.dto.DossierDto();
        dossierDto.setBudgetGlobal("100000");
        when(projectClient.getDossier(dossierId)).thenReturn(dossierDto);
        when(pwinRepo.findByDossierId(dossierId)).thenReturn(Optional.of(pwinScore));
        when(analyseRepo.findByDossierId(dossierId)).thenReturn(Optional.of(new AnalyseDossier()));
        
        // Mock Target Manager
        when(projectClient.getTargetManager(anyDouble())).thenReturn(Map.of("role", "Directeur de Pôle", "threshold", 50000));
        
        // Mock Génération du rapport
        tn.rihab.analysteservice.model.NoGoReport noGoResp = new tn.rihab.analysteservice.model.NoGoReport();
        noGoResp.setDocxPath("/tmp/nogo.docx");
        when(noGoReportService.generate(eq(dossierId), any(), any(), any(), anyString())).thenReturn(noGoResp);

        Map<String, String> request = Map.of("analysteName", "Ghada");

        // Appel
        ResponseEntity<Map<String, Object>> response = scoringController.confirmNoGo(dossierId, request);

        // Vérifications
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        verify(noGoReportService, times(1)).generate(eq(dossierId), any(), any(), any(), eq("Ghada"));
        verify(projectClient, times(1)).notifyManagerNoGo(dossierId);
        verify(sseService, times(1)).sendEvent(eq(dossierId), eq("PIPELINE_STOPPED_NOGO"), anyMap());
    }
}
