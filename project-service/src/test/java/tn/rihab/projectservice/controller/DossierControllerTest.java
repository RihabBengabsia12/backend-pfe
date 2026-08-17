package tn.rihab.projectservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;
import tn.rihab.projectservice.model.DossierStatus;
import tn.rihab.projectservice.model.entity.Dossier;
import tn.rihab.projectservice.service.DossierService;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
class DossierControllerTest {

    @Mock
    private DossierService dossierService;

    @InjectMocks
    private DossierController dossierController;

    @BeforeEach
    void setUp() {
        // Simuler un utilisateur connecté pour le test
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("analyste@projectiq.tn", "password")
        );
    }

    // --- CAS SENSIBLE : TF-07 - Dépôt d'un fichier valide (PDF/DOCX) ---
    @Test
    void TF_07_01_UploadDossier_FichierValide() {
        // Préparation d'un fichier PDF valide de taille correcte
        MockMultipartFile validFile = new MockMultipartFile(
                "tdr",
                "cahier_des_charges.pdf",
                "application/pdf",
                "Contenu valide du PDF".getBytes()
        );

        Dossier mockDossier = Dossier.builder()
                .id(UUID.randomUUID())
                .status(DossierStatus.UPLOADED)
                .createdByEmail("analyste@projectiq.tn")
                .dtLimSoum(LocalDate.parse("2026-12-31"))
                .build();

        when(dossierService.createDossierWithAsyncParsing(any(), any(), any(), any(), any()))
                .thenReturn(mockDossier);

        // Action
        ResponseEntity<Dossier> response = dossierController.upload(validFile, "2026-12-31", "Projet Test", false);

        // Vérification
        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(DossierStatus.UPLOADED, response.getBody().getStatus());
        
        verify(dossierService, times(1)).createDossierWithAsyncParsing(any(), any(), any(), any(), any());
    }

    // --- CAS SENSIBLE : TF-08 - Dépôt refusé (Format invalide, ex: Image PNG) ---
    @Test
    void TF_08_01_UploadDossier_FormatInvalide_Image() {
        // Préparation d'un fichier image (non supporté)
        MockMultipartFile imageFile = new MockMultipartFile(
                "tdr",
                "capture.png",
                "image/png",
                "Données image".getBytes()
        );

        // Action & Vérification : Doit lever une IllegalArgumentException
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            dossierController.upload(imageFile, "2026-12-31", "Projet Test", false);
        });

        assertTrue(exception.getMessage().contains("Format non supporté"));
        assertTrue(exception.getMessage().contains("PDF ou DOCX uniquement"));
        
        // Le service ne doit jamais être appelé
        verify(dossierService, never()).createDossierWithAsyncParsing(any(), any(), any(), any(), any());
    }

    // --- CAS SENSIBLE : TF-08 - Dépôt refusé (Fichier > 100 Mo) ---
    @Test
    void TF_08_02_UploadDossier_FichierTropVolumineux() {
        // Préparation d'un fichier valide (PDF) mais avec une taille > 100 Mo
        // Pour MockMultipartFile, on surcharge la méthode getSize() via un spy/mock ou on crée un grand tableau
        // Création d'un faux MultipartFile avec une taille simulée de 105 Mo (105 * 1024 * 1024 = 110100480 bytes)
        MultipartFile hugeFile = mock(MultipartFile.class);
        when(hugeFile.isEmpty()).thenReturn(false);
        when(hugeFile.getOriginalFilename()).thenReturn("gros_fichier.pdf");
        when(hugeFile.getSize()).thenReturn(110100480L); // 105 Mo

        // Action & Vérification
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            dossierController.upload(hugeFile, "2026-12-31", "Projet Test", false);
        });

        assertTrue(exception.getMessage().contains("Fichier trop volumineux"));
        assertTrue(exception.getMessage().contains("100 Mo maximum"));
        
        verify(dossierService, never()).createDossierWithAsyncParsing(any(), any(), any(), any(), any());
    }

    // --- CAS SENSIBLE : TF-12 - Analyse par lot (Batch) ---
    @Test
    void TF_12_01_AnalyseParLot_Succes() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        java.util.List<UUID> batchIds = java.util.List.of(id1, id2);

        ResponseEntity<java.util.Map<String, String>> response = dossierController.launchBatchAnalysis(batchIds);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().get("message").contains("2 dossiers"));
        
        // On vérifie que le statut a bien été mis à jour en PARSING_INITIAL pour les deux
        verify(dossierService).updateStatus(id1, DossierStatus.PARSING_INITIAL);
        verify(dossierService).updateStatus(id2, DossierStatus.PARSING_INITIAL);
    }
}
