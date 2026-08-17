package tn.rihab.projectservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.rihab.projectservice.model.entity.AnonymizationToken;
import tn.rihab.projectservice.repository.AnonymizationTokenRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
class AnonymizationServiceTest {

    @Mock
    private AnonymizationTokenRepository tokenRepository;

    @InjectMocks
    private AnonymizationService anonymizationService;

    // --- TEST NON-FONCTIONNEL : TNF-03 - Masquage DLP ---

    @Test
    void TNF_03_01_Masking_Sensitive_Data() {
        String originalText = "Veuillez contacter le directeur au 06.12.34.56.78 ou par email sur contact@entreprise.com. Son IBAN est FR7612345678901234567890123.";

        // On vérifie que le repository est appelé pour sauvegarder les tokens
        when(tokenRepository.save(any(AnonymizationToken.class))).thenAnswer(i -> i.getArguments()[0]);

        String maskedText = anonymizationService.maskText(originalText);

        // Vérifications
        assertNotNull(maskedText);
        
        // Le texte masqué ne doit plus contenir les vraies informations
        assertFalse(maskedText.contains("06.12.34.56.78"));
        assertFalse(maskedText.contains("contact@entreprise.com"));
        assertFalse(maskedText.contains("FR7612345678901234567890123"));

        // Le texte masqué doit contenir les balises DLP correspondantes
        assertTrue(maskedText.contains("[DLP-PHONE-"));
        assertTrue(maskedText.contains("[DLP-EMAIL-"));
        assertTrue(maskedText.contains("[DLP-IBAN-"));

        // Vérifie qu'on a bien sauvegardé 3 tokens dans la base (le coffre-fort DLP)
        verify(tokenRepository, times(3)).save(any(AnonymizationToken.class));
    }

    @Test
    void TNF_03_02_Unmasking_Sensitive_Data() {
        String uuid1 = "123e4567-e89b-12d3-a456-426614174000";
        String uuid2 = "123e4567-e89b-12d3-a456-426614174001";
        
        String maskedText = "L'email est [DLP-EMAIL-" + uuid1 + "] et le tel [DLP-PHONE-" + uuid2 + "].";

        AnonymizationToken emailToken = AnonymizationToken.builder()
                .token("[DLP-EMAIL-" + uuid1 + "]")
                .originalValue("admin@test.com")
                .build();
                
        AnonymizationToken phoneToken = AnonymizationToken.builder()
                .token("[DLP-PHONE-" + uuid2 + "]")
                .originalValue("+33611223344")
                .build();

        when(tokenRepository.findById("[DLP-EMAIL-" + uuid1 + "]")).thenReturn(Optional.of(emailToken));
        when(tokenRepository.findById("[DLP-PHONE-" + uuid2 + "]")).thenReturn(Optional.of(phoneToken));

        String unmaskedText = anonymizationService.unmaskText(maskedText);

        // Vérifications
        assertTrue(unmaskedText.contains("admin@test.com"));
        assertTrue(unmaskedText.contains("+33611223344"));
        
        // Les balises doivent avoir disparu
        assertFalse(unmaskedText.contains("[DLP-EMAIL-"));
        assertFalse(unmaskedText.contains("[DLP-PHONE-"));
    }
}
