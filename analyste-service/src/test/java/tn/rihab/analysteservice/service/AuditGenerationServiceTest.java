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
import tn.rihab.analysteservice.dto.AuditEntryDto;
import tn.rihab.analysteservice.dto.DossierDto;
import tn.rihab.analysteservice.dto.ia.AuditReportResponseDto;
import tn.rihab.analysteservice.messaging.AnalysteEventPublisher;
import tn.rihab.analysteservice.model.PwinScore;
import tn.rihab.analysteservice.repository.AnalyseDossierRepository;
import tn.rihab.analysteservice.repository.IaAuditLogRepository;
import tn.rihab.analysteservice.repository.PwinScoreRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
class AuditGenerationServiceTest {

    @Mock private ProjectServiceClient projectClient;
    @Mock private IaServiceClient iaClient;
    @Mock private PackStorageService storageService;
    @Mock private AnalysteEventPublisher publisher;
    @Mock private PwinScoreRepository pwinRepo;
    @Mock private AnalyseDossierRepository analyseRepo;
    @Mock private IaAuditLogRepository auditLogRepo;

    @InjectMocks
    private AuditGenerationService auditGenerationService;

    private UUID dossierId;
    private DossierDto dossier;
    private PwinScore pwin;

    @BeforeEach
    void setUp() throws Exception {
        dossierId = UUID.randomUUID();

        dossier = new DossierDto();
        dossier.setId(dossierId);
        dossier.setIntituleOffre("Projet Test");
        dossier.setClient("Client Test");

        pwin = new PwinScore();
        pwin.setScoreGlobal(80.0);
        pwin.setDecisionAuto("GO");

        lenient().when(projectClient.getDossier(dossierId)).thenReturn(dossier);
        AuditEntryDto auditEntry = new AuditEntryDto();
        auditEntry.setDossierId(dossierId);
        auditEntry.setAction("FIELD_CORRECTED");
        auditEntry.setDetail("Correction humaine");
        
        lenient().when(projectClient.getAuditHistory(dossierId)).thenReturn(List.of(auditEntry));
        lenient().when(pwinRepo.findByDossierId(dossierId)).thenReturn(Optional.of(pwin));

        AuditReportResponseDto aiReport = new AuditReportResponseDto();
        aiReport.setNarratif("Analyse générée par Claude");
        aiReport.setPointsAmelioration("Points...");
        lenient().when(iaClient.generateAuditReport(any())).thenReturn(aiReport);

        lenient().when(storageService.uploadBytes(anyString(), anyString(), any(), anyString())).thenReturn("/minio/path/audit.docx");
    }

    // --- CAS SENSIBLE : TF-20 - Génération rapport d'audit ---
    @Test
    void TF_20_01_Generation_Rapport_Audit_Succes() {
        // Appel de la méthode à tester
        auditGenerationService.generateAuditReport(dossierId);

        // Vérifications
        verify(projectClient, times(1)).getDossier(dossierId);
        verify(projectClient, times(1)).getAuditHistory(dossierId);
        verify(iaClient, times(1)).generateAuditReport(any());
        
        // Vérifier que le document est bien sauvegardé (uploadBytes)
        verify(storageService, times(1)).uploadBytes(eq("rapports-audit"), anyString(), any(), eq("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        
        // Vérifier que l'événement est bien publié
        verify(publisher, times(1)).publishAuditGenerated(eq(dossierId), eq("/minio/path/audit.docx"));
    }
}
