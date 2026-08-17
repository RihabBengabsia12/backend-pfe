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
import tn.rihab.analysteservice.dto.ia.ApoTextsResponseDto;
import tn.rihab.analysteservice.dto.ia.ChecklistResponseDto;
import tn.rihab.analysteservice.dto.ia.MethodologieResponseDto;
import tn.rihab.analysteservice.messaging.AnalysteEventPublisher;
import tn.rihab.analysteservice.model.AnalyseDossier;
import tn.rihab.analysteservice.model.ApoData;
import tn.rihab.analysteservice.model.MatchingResult;
import tn.rihab.analysteservice.model.PwinScore;
import tn.rihab.analysteservice.repository.ApoDataRepository;
import tn.rihab.analysteservice.repository.IaAuditLogRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
class ApoAssemblyServiceTest {

    @Mock private IaServiceClient iaClient;
    @Mock private DocumentExportService exportService;
    @Mock private PackGeneratorService packGenerator;
    @Mock private ChecklistService checklistService;
    @Mock private AnalysteEventPublisher publisher;
    @Mock private ApoDataRepository apoDataRepo;
    @Mock private ProjectServiceClient projectClient;
    @Mock private IaAuditLogRepository auditLogRepo;

    @InjectMocks
    private ApoAssemblyService apoAssemblyService;

    private UUID dossierId;
    private DossierDto dossier;
    private AnalyseDossier analyse;
    private MatchingResult matching;
    private PwinScore pwin;

    @BeforeEach
    void setUp() {
        dossierId = UUID.randomUUID();
        dossier = new DossierDto();
        dossier.setId(dossierId);
        dossier.setIntituleOffre("Projet Eau Maroc");
        dossier.setBailleurs("Banque Mondiale");
        dossier.setPays("Maroc");

        analyse = new AnalyseDossier();
        analyse.setNoteMinimale("75");

        matching = new MatchingResult();
        matching.setTauxCouvertureCompetences(0.85);

        pwin = new PwinScore();
        pwin.setScoreGlobal(90.0);
        pwin.setDecisionAuto("GO");

        // Mock repository
        lenient().when(apoDataRepo.findByDossierId(any())).thenReturn(Optional.empty());
        lenient().when(apoDataRepo.save(any(ApoData.class))).thenAnswer(i -> i.getArgument(0));

        // Mock IA services
        ApoTextsResponseDto texts = new ApoTextsResponseDto();
        texts.setResumeContexteObjectifs("Résumé du contexte...");
        lenient().when(iaClient.generateApoTexts(any())).thenReturn(texts);

        MethodologieResponseDto methodo = new MethodologieResponseDto();
        methodo.setSection1_contexteEnjeux("Compréhension...");
        lenient().when(iaClient.generateMethodologie(any())).thenReturn(methodo);

        // Mock Export Services
        lenient().when(exportService.exportApo(any(), anyString())).thenReturn("/tmp/apo.docx");
        lenient().when(exportService.exportMethodologie(any(), any(ApoData.class), any(DossierDto.class)))
                .thenReturn("/tmp/methodo.docx");
        lenient().when(exportService.exportRapportResultat(any(), any(), anyString())).thenReturn("/tmp/rapport.docx");

        // Mock Checklist & Pack
        lenient().when(checklistService.generate(anyString(), anyString())).thenReturn(new ChecklistResponseDto());
        lenient().when(packGenerator.generatePack(any(), anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn("/tmp/pack_final.zip");
                
        // Mock Decapsulation
        lenient().when(projectClient.decapsulate(any(), anyString())).thenAnswer(i -> i.getArgument(1));
    }

    // --- CAS SENSIBLE : TF-16 - Génération Livrable / Pack ---
    @Test
    void TF_16_01_GenerationPack_Succes() {
        // Appel de la méthode
        apoAssemblyService.assembleAndGenerate(dossierId, dossier, analyse, matching, pwin);

        // Vérifications que tous les services ont été appelés dans le bon ordre
        verify(iaClient, times(1)).generateApoTexts(any());
        verify(iaClient, times(1)).generateMethodologie(any());
        verify(exportService, times(1)).exportApo(any(), eq("Projet Eau Maroc"));
        verify(exportService, times(1)).exportMethodologie(any(), any(ApoData.class), eq(dossier));
        verify(exportService, times(1)).exportRapportResultat(any(), any(), eq("Projet Eau Maroc"));
        verify(packGenerator, times(1)).generatePack(eq(dossierId), eq("/tmp/apo.docx"), eq("/tmp/methodo.docx"), eq("/tmp/rapport.docx"), any(), eq("Projet Eau Maroc"));
        verify(publisher, times(1)).publishApoGenerated(eq(dossierId), eq("/tmp/apo.docx"), eq("/tmp/methodo.docx"), eq("/tmp/rapport.docx"), eq("/tmp/pack_final.zip"));
    }
}
