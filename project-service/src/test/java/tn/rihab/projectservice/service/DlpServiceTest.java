package tn.rihab.projectservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.rihab.projectservice.model.entity.AnonymizationDict;
import tn.rihab.projectservice.repository.AnonymizationDictRepository;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
class DlpServiceTest {

    @Mock
    private AnonymizationDictRepository dictRepository;
    @Mock
    private AuditTrailService auditTrailService;

    @InjectMocks
    private DlpService dlpService;

    private AnonymizationDict dictWord;
    private UUID dossierId;

    @BeforeEach
    void setUp() {
        dossierId = UUID.randomUUID();
        dictWord = AnonymizationDict.builder()
                .id(UUID.randomUUID())
                .originalWord("Confidentiel")
                .replacementCode("***CODE_A1B2C3D4***")
                .isActive(true)
                .build();
    }

    // --- CAS SENSIBLE : TF-06 (Partie 1) - Ajout d'un nouveau mot ---
    @Test
    void TF_06_01_DLP_AjoutNouveauMotDictionnaire() {
        String nouveauMot = "SecretDéfense";
        
        when(dictRepository.existsByOriginalWordIgnoreCase(nouveauMot)).thenReturn(false);
        when(dictRepository.save(any(AnonymizationDict.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AnonymizationDict result = dlpService.addWord(nouveauMot);

        assertNotNull(result);
        assertEquals(nouveauMot, result.getOriginalWord());
        assertNotNull(result.getReplacementCode());
        assertTrue(result.getReplacementCode().startsWith("***CODE_"));
        assertTrue(result.getIsActive());
        
        verify(dictRepository).save(any(AnonymizationDict.class));
    }

    // --- CAS SENSIBLE : TF-06 (Partie 2) - Vérification de l'encapsulation ---
    @Test
    void TF_06_02_DLP_EncapsulationEtDecapsulationDuTexteSensible() {
        // 1. Simulation : Le dictionnaire contient le mot "Confidentiel" actif
        when(dictRepository.findByIsActiveTrue()).thenReturn(Collections.singletonList(dictWord));
        
        // 2. Texte contenant le mot sensible (avec de la ponctuation et casse différente)
        String texteOriginal = "Ce dossier est confidentiel et ne doit pas être divulgué.";
        
        // --- TEST ENCAPSULATION ---
        String texteMasque = dlpService.encapsulate(texteOriginal, dossierId);
        
        // Vérifications
        assertNotNull(texteMasque);
        assertTrue(texteMasque.contains("***CODE_A1B2C3D4***"), "Le code de remplacement doit être présent.");
        assertFalse(texteMasque.toLowerCase().contains("confidentiel"), "Le mot sensible original ne doit plus apparaître dans le texte.");
        
        // Vérifier que l'audit a bien été enregistré
        verify(auditTrailService).log(eq(dossierId), eq("DLP_ENCAPSULATION"), eq("system"), contains("1 remplacements"), isNull());

        // --- TEST DECAPSULATION ---
        // Pour la décapsulation, le service lit tout le dictionnaire
        when(dictRepository.findAll()).thenReturn(Collections.singletonList(dictWord));
        
        String texteRestaure = dlpService.decapsulate(texteMasque, dossierId);
        
        // Vérifications
        assertNotNull(texteRestaure);
        assertTrue(texteRestaure.toLowerCase().contains("confidentiel"), "Le mot sensible original doit être restauré.");
        assertFalse(texteRestaure.contains("***CODE_"), "Le code de remplacement ne doit plus exister.");
        
        // Vérifier l'audit de décapsulation
        verify(auditTrailService).log(eq(dossierId), eq("DLP_DECAPSULATION"), eq("system"), contains("1 restaurations"), isNull());
    }
}
