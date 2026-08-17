package tn.rihab.analysteservice.matching;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.rihab.analysteservice.client.IaServiceClient;
import tn.rihab.analysteservice.dto.DossierDto;
import tn.rihab.analysteservice.dto.ia.RequirementsResponseDto;
import tn.rihab.analysteservice.dto.ia.MatrixResponseDto;
import tn.rihab.analysteservice.matching.matchers.ClientMatcher;
import tn.rihab.analysteservice.matching.matchers.CompetencesMatcher;
import tn.rihab.analysteservice.matching.matchers.ExpertsMatcher;
import tn.rihab.analysteservice.matching.matchers.ReferencesMatcher;
import tn.rihab.analysteservice.model.MatchingResult;
import tn.rihab.analysteservice.model.ScoringConfig;
import tn.rihab.analysteservice.repository.CompetenceRepository;
import tn.rihab.analysteservice.repository.ExpertProfilRepository;
import tn.rihab.analysteservice.repository.IaAuditLogRepository;
import tn.rihab.analysteservice.repository.MatchingResultRepository;
import tn.rihab.analysteservice.repository.ReferenceRepository;
import tn.rihab.analysteservice.scoring.ScoringConfigService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
class MatchingEngineTest {

    @Mock private IaServiceClient iaClient;
    @Mock private CompetencesMatcher competencesMatcher;
    @Mock private ReferencesMatcher referencesMatcher;
    @Mock private ExpertsMatcher expertsMatcher;
    @Mock private ClientMatcher clientMatcher;
    @Mock private MatchingResultRepository matchingRepo;
    @Mock private ObjectMapper objectMapper;
    @Mock private ScoringConfigService scoringConfigService;
    @Mock private CompetenceRepository competenceRepository;
    @Mock private ReferenceRepository referenceRepository;
    @Mock private ExpertProfilRepository expertProfilRepository;
    @Mock private IaAuditLogRepository auditLogRepo;

    @InjectMocks
    private MatchingEngine matchingEngine;

    private DossierDto dossier;
    private ScoringConfig config;

    @BeforeEach
    void setUp() throws Exception {
        dossier = new DossierDto();
        dossier.setId(UUID.randomUUID());
        dossier.setPays("Maroc");
        dossier.setBudgetGlobal("1M");

        config = new ScoringConfig();
        config.setSeuilCompatCompetences(0.60);
        config.setSeuilCompatExperts(0.50);

        // Mocks pour les dépôts (référentiels)
        lenient().when(competenceRepository.findByActifTrue()).thenReturn(List.of());
        lenient().when(referenceRepository.findByActifTrue()).thenReturn(List.of());
        lenient().when(expertProfilRepository.findByActifTrue()).thenReturn(List.of());

        // Mock IA Extraction Requirements
        RequirementsResponseDto reqResp = new RequirementsResponseDto();
        reqResp.setQualifsExigees(List.of("Java", "Spring"));
        lenient().when(iaClient.extractRequirements(any())).thenReturn(reqResp);

        // Mock IA Génération Matrice
        lenient().when(iaClient.generateMatrix(any())).thenReturn(new MatrixResponseDto());

        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("[]");
        lenient().when(matchingRepo.findByDossierId(any())).thenReturn(Optional.empty());
        lenient().when(matchingRepo.save(any(MatchingResult.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(scoringConfigService.getCurrent()).thenReturn(config);
    }

    // --- CAS SENSIBLE : TF-15 - Matching (Compétences, Experts, etc.) ---
    @Test
    void TF_15_01_MatchingPartenaires_Succes() {
        // Mock des Matchers avec de bons scores (Alignement OUI)
        when(competencesMatcher.match(any())).thenReturn(new tn.rihab.analysteservice.matching.matchers.CompetencesMatcher.CompetencesMatchResult(0.85, "{}"));
        when(referencesMatcher.match(any(), any(), any())).thenReturn(new tn.rihab.analysteservice.matching.matchers.ReferencesMatcher.ReferencesMatchResult("[]", List.of()));
        when(expertsMatcher.match(any(), any())).thenReturn(new tn.rihab.analysteservice.matching.matchers.ExpertsMatcher.ExpertsMatchResult(0.90, "[]"));
        when(clientMatcher.match(any())).thenReturn(new tn.rihab.analysteservice.matching.matchers.ClientMatcher.ClientMatchResult(2, 5));

        // Appel
        MatchingResult result = matchingEngine.runMatching(dossier, "Contenu texte...");

        // Vérifications
        assertNotNull(result);
        assertEquals(0.85, result.getTauxCouvertureCompetences(), "La couverture des compétences doit correspondre au matcher (85%)");
        assertEquals(0.90, result.getTauxCouvertureExperts(), "La couverture des experts doit correspondre au matcher (90%)");
        assertEquals(2, result.getRelationClientNiveau(), "La relation client doit être de niveau 2");
        assertEquals("Oui — aligné", result.getAlignementStrategique(), "L'alignement doit être OUI car couverture > 80%");
        assertTrue(Boolean.TRUE.equals(result.getCompatibleMethodologie()), "Le dossier doit être compatible pour la phase méthodologie");
        
        // Vérifier que la matrice IA a bien été appelée
        verify(iaClient, times(1)).generateMatrix(any());
        verify(matchingRepo, times(1)).save(any(MatchingResult.class));
    }
}
