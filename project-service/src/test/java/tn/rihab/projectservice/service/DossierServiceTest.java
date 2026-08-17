package tn.rihab.projectservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.rihab.projectservice.dto.ValidateP1RequestDto;
import tn.rihab.projectservice.messaging.EventPublisher;
import tn.rihab.projectservice.model.DossierStatus;
import tn.rihab.projectservice.model.entity.Dossier;
import tn.rihab.projectservice.model.entity.ExtractionMetadata;
import tn.rihab.projectservice.repository.DossierRepository;
import tn.rihab.projectservice.repository.ExtractionMetadataRepository;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
class DossierServiceTest {

    @Mock
    private DossierRepository dossierRepository;
    @Mock
    private ExtractionMetadataRepository metadataRepository;
    @Mock
    private EventPublisher eventPublisher;
    @Mock
    private StorageService storageService;
    @Mock
    private AnonymizationService anonymizationService;

    @InjectMocks
    private DossierService dossierService;

    private UUID dossierId;
    private Dossier mockDossier;
    private ExtractionMetadata mockMetadataPays;

    @BeforeEach
    void setUp() {
        dossierId = UUID.randomUUID();
        
        mockDossier = Dossier.builder()
                .id(dossierId)
                .status(DossierStatus.PARSING_INITIAL)
                .dtLimSoum(LocalDate.parse("2026-12-31"))
                .build();
                
        mockMetadataPays = ExtractionMetadata.builder()
                .id(UUID.randomUUID())
                .dossierId(dossierId)
                .fieldName("PAYS")
                .valeurClaude("Tunisie") // Ce que l'IA a trouvé
                .valeurFinale("Tunisie") // La valeur actuelle avant validation
                .humanModified(false)
                .build();
    }

    // --- CAS SENSIBLE : TF-11 - Correction manuelle de l'extraction P1 ---
    @Test
    void TF_11_01_CorrectionManuelleP1_Succes() {
        // Préparer la requête où l'analyste corrige "Tunisie" par "Maroc"
        ValidateP1RequestDto request = new ValidateP1RequestDto();
        ValidateP1RequestDto.ChampValide champPays = new ValidateP1RequestDto.ChampValide();
        champPays.setValeur("Maroc");
        champPays.setHumanModified(true); // L'analyste l'a modifié manuellement
        request.setChamps(Map.of("PAYS", champPays));

        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(mockDossier));
        when(metadataRepository.findByDossierIdAndFieldName(dossierId, "PAYS")).thenReturn(Optional.of(mockMetadataPays));
        when(dossierRepository.save(any(Dossier.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Appel
        Dossier result = dossierService.validateP1(dossierId, request);

        // Vérifications sur le dossier
        assertNotNull(result);
        assertEquals(DossierStatus.INDEXED, result.getStatus(), "Le dossier doit passer à l'état INDEXED après validation");
        assertEquals("Maroc", result.getPays(), "Le pays du dossier doit être mis à jour avec la correction");

        // Vérifications sur les métadonnées (le champ modifié)
        verify(metadataRepository).save(argThat(meta -> 
            meta.getFieldName().equals("PAYS") && 
            meta.getValeurFinale().equals("Maroc") && 
            Boolean.TRUE.equals(meta.getHumanModified()) // La trace de la modification humaine est conservée
        ));

        // L'événement RabbitMQ pour la suite (P2) doit être déclenché
        verify(eventPublisher, times(1)).publishDossierIndexed(dossierId);
    }

    @Test
    void TNF_03_DossierPrive_UtiliseLaCopieAnonymiseePersistante() {
        mockDossier.setIsPrivate(true);
        mockDossier.setDocumentTextPath("documents-texte/" + dossierId + "/document_full.txt");
        mockDossier.setAnonymizedTextPath("documents-texte/" + dossierId + "/document_anonymized.txt");
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(mockDossier));
        when(storageService.downloadText(mockDossier.getAnonymizedTextPath()))
                .thenReturn("Contact : [DLP-EMAIL-123]");

        String result = dossierService.getDocumentText(dossierId);

        assertEquals("Contact : [DLP-EMAIL-123]", result);
        verify(storageService).downloadText(mockDossier.getAnonymizedTextPath());
        verify(storageService, never()).downloadText(mockDossier.getDocumentTextPath());
        verifyNoInteractions(anonymizationService);
    }

    @Test
    void TNF_03_DossierPriveExistant_CreeUneSeuleCopieAnonymisee() {
        mockDossier.setIsPrivate(true);
        mockDossier.setDocumentTextPath("documents-texte/" + dossierId + "/document_full.txt");
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(mockDossier));
        when(storageService.downloadText(mockDossier.getDocumentTextPath()))
                .thenReturn("Contact : projetiq@example.com");
        when(anonymizationService.maskText("Contact : projetiq@example.com"))
                .thenReturn("Contact : [DLP-EMAIL-123]");
        when(storageService.uploadText(
                eq("documents-texte"),
                eq(dossierId + "/document_anonymized.txt"),
                eq("Contact : [DLP-EMAIL-123]")
        )).thenReturn("documents-texte/" + dossierId + "/document_anonymized.txt");
        when(storageService.downloadText("documents-texte/" + dossierId + "/document_anonymized.txt"))
                .thenReturn("Contact : [DLP-EMAIL-123]");

        String result = dossierService.getDocumentText(dossierId);

        assertEquals("Contact : [DLP-EMAIL-123]", result);
        assertEquals("documents-texte/" + dossierId + "/document_anonymized.txt", mockDossier.getAnonymizedTextPath());
        verify(anonymizationService, times(1)).maskText("Contact : projetiq@example.com");
        verify(dossierRepository).save(mockDossier);
    }
}
