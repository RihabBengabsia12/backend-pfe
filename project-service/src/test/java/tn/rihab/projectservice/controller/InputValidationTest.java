package tn.rihab.projectservice.controller;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tn.rihab.projectservice.service.DossierService;
import tn.rihab.projectservice.service.ValidationService;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DossierController.class)
@AutoConfigureMockMvc(addFilters = false) // On désactive la sécurité pour se concentrer sur la validation
@TestMethodOrder(MethodOrderer.MethodName.class)
class InputValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DossierService dossierService;

    @MockitoBean
    private ValidationService validationService;

    // --- Mocks pour la sécurité ---
    @MockitoBean
    private tn.rihab.projectservice.config.JwtUtils jwtUtils;

    @MockitoBean
    private tn.rihab.projectservice.config.JwtAuthenticationFilter jwtAuthenticationFilter;

    // --- TEST NON-FONCTIONNEL : TNF-04 - Validation entrées (fichiers/champs) ---

    @Test
    void TNF_04_01_Rejet_Fichier_Vide() throws Exception {
        // Création d'un fichier vide
        MockMultipartFile emptyFile = new MockMultipartFile("tdr", "test.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/dossiers/upload")
                        .file(emptyFile)
                        .param("dateLimite", "2026-12-31")
                        .param("isPrivate", "false"))
                // Spring Boot (ou notre gestionnaire d'exceptions) doit renvoyer une erreur 4xx (ou 500 si IllegalArgumentException n'est pas catchée globalement, mais le test capture l'exception)
                .andExpect(result -> assertTrue(result.getResolvedException() instanceof IllegalArgumentException))
                .andExpect(result -> assertTrue(result.getResolvedException().getMessage().contains("Fichier vide ou manquant")));
    }

    @Test
    void TNF_04_02_Rejet_Mauvaise_Extension() throws Exception {
        // Création d'un fichier avec une extension interdite (.exe)
        MockMultipartFile badExtensionFile = new MockMultipartFile("tdr", "virus.exe", "application/x-msdownload", "fake content".getBytes());

        mockMvc.perform(multipart("/api/dossiers/upload")
                        .file(badExtensionFile)
                        .param("dateLimite", "2026-12-31"))
                .andExpect(result -> assertTrue(result.getResolvedException() instanceof IllegalArgumentException))
                .andExpect(result -> assertTrue(result.getResolvedException().getMessage().contains("Format non supporté")));
    }
}
