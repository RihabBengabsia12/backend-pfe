package tn.rihab.projectservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.rihab.projectservice.client.IaServiceClient;
import tn.rihab.projectservice.dto.ChampResult;
import tn.rihab.projectservice.dto.ExtractionRequestDto;
import tn.rihab.projectservice.dto.ExtractionResponseDto;
import tn.rihab.projectservice.model.entity.ExtractionMetadata;
import tn.rihab.projectservice.repository.AiLogRepository;
import tn.rihab.projectservice.repository.ExtractionMetadataRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
class ExtractionServiceTest {

    @Mock
    private IaServiceClient iaServiceClient;
    @Mock
    private ExtractionMetadataRepository metadataRepository;
    @Mock
    private AuditTrailService auditTrailService;
    @Mock
    private AiLogRepository aiLogRepository;

    @InjectMocks
    private ExtractionService extractionService;

    private UUID dossierId;
    private ExtractionMetadata mockMetadata;

    @BeforeEach
    void setUp() {
        dossierId = UUID.randomUUID();
        mockMetadata = ExtractionMetadata.builder()
                .dossierId(dossierId)
                .fieldName("PAYS")
                .valeurClaude("France")
                .reextractionCount(2) // Déjà ré-extrait 2 fois
                .build();
                
        // Les champs @Value doivent être injectés manuellement dans les tests Mockito purs
        ReflectionTestUtils.setField(extractionService, "tjmMin", 350.0);
        ReflectionTestUtils.setField(extractionService, "tjmMax", 3000.0);
    }

    // --- CAS SENSIBLE : TF-09 - Lancement Extraction P1 ---
    @Test
    void TF_09_01_LancementExtractionP1_Succes() {
        // Création des ChampResult avec le constructeur par défaut + setters
        ChampResult paysResult = new ChampResult();
        paysResult.setValeur("Maroc");
        paysResult.setConfiance(0.95);
        paysResult.setSource("Page 1");

        ChampResult budgetResult = new ChampResult();
        budgetResult.setValeur("50000");
        budgetResult.setConfiance(0.8);
        budgetResult.setSource("Page 2");

        ChampResult hmResult = new ChampResult();
        hmResult.setValeur("5");
        hmResult.setConfiance(0.9);
        hmResult.setSource("Page 2");

        // Mock de la réponse de l'IA
        ExtractionResponseDto mockResponse = new ExtractionResponseDto();
        mockResponse.setChamps(Map.of(
                "PAYS", paysResult,
                "BUDGET_GLOBAL", budgetResult,
                "HOMMES_MOIS", hmResult
        ));
        
        when(iaServiceClient.extractPhase1(any(ExtractionRequestDto.class))).thenReturn(mockResponse);

        // Appel du service
        ExtractionResponseDto result = extractionService.extractPhase1(dossierId, "Texte du document complet...");

        // Vérifications
        assertNotNull(result);
        assertEquals(3, result.getChamps().size());
        assertEquals("Maroc", result.getChamps().get("PAYS").getValeur());
        
        // Vérifier que le TJM a bien été calculé automatiquement
        // 50000 / 5 / 20 = 500
        assertNotNull(result.getTjmImplicite());
        assertEquals(500.0, result.getTjmImplicite());

        // Vérifier les sauvegardes (Méta et Audit)
        verify(iaServiceClient, times(1)).extractPhase1(any());
        verify(metadataRepository, atLeastOnce()).save(any(ExtractionMetadata.class));
        verify(auditTrailService, times(1)).log(eq(dossierId), eq("PARSING_COMPLETED"), anyString(), anyString(), isNull());
    }

    // --- CAS SENSIBLE : TF-10 - Refus de la 3ème ré-extraction ---
    @Test
    void TF_10_01_Refus3emeReextraction_Bloque() {
        // Le metadata mocké a déjà reextractionCount = 2
        when(metadataRepository.findByDossierIdAndFieldName(dossierId, "PAYS"))
                .thenReturn(Optional.of(mockMetadata));

        // L'action doit lever une exception
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            extractionService.reextractField(dossierId, "PAYS", "Texte du document...");
        });

        // Vérification du message d'erreur
        assertTrue(exception.getMessage().contains("Max 2 ré-extractions atteint"));

        // L'IA ne doit JAMAIS être appelée car c'est bloqué avant
        verify(iaServiceClient, never()).reextractField(any());
    }
}
