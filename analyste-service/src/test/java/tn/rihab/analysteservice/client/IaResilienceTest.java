package tn.rihab.analysteservice.client;

import feign.Client;
import feign.Feign;
import feign.Request;
import feign.Response;
import feign.codec.Decoder;
import feign.codec.Encoder;
import org.junit.jupiter.api.Test;
import tn.rihab.analysteservice.config.FeignConfig;
import tn.rihab.analysteservice.dto.ia.ExtractionRequestDto;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IaResilienceTest {

    // --- TEST NON-FONCTIONNEL : TNF-05 - Résilience IA (Retry) ---

    @Test
    void TNF_05_01_Retry_Sur_Service_Indisponible() throws IOException {
        // Préparation d'une fausse réponse 503 (Service Unavailable)
        Response serviceUnavailableResponse = Response.builder()
                .status(503)
                .reason("Service Unavailable")
                .request(Request.create(Request.HttpMethod.POST, "/extract/phase2", new HashMap<>(), null, StandardCharsets.UTF_8, null))
                .headers(new HashMap<>())
                .build();

        // On mock le client HTTP sous-jacent de Feign
        Client mockClient = mock(Client.class);
        when(mockClient.execute(any(Request.class), any(Request.Options.class)))
                .thenReturn(serviceUnavailableResponse);

        FeignConfig config = new FeignConfig();
        Encoder mockEncoder = mock(Encoder.class);
        Decoder mockDecoder = mock(Decoder.class);

        // On instancie le client Feign manuellement avec notre configuration (sans charger tout Spring)
        IaServiceClient iaServiceClient = Feign.builder()
                .client(mockClient)
                .encoder(mockEncoder)
                .decoder(mockDecoder)
                .contract(new org.springframework.cloud.openfeign.support.SpringMvcContract()) // <-- Pour supporter @PostMapping, @GetMapping, etc.
                .retryer(config.feignRetryer())
                .errorDecoder(config.feignErrorDecoder())
                .options(config.feignOptions())
                .target(IaServiceClient.class, "http://localhost:8000");

        ExtractionRequestDto request = new ExtractionRequestDto();
        request.setDocumentText("Test content");

        // L'appel final va échouer car on a épuisé les retries (max 3 tentatives)
        assertThrows(feign.RetryableException.class, () -> {
            iaServiceClient.extractPhase2(request);
        });

        // VÉRIFICATION : Feign a bien tenté d'appeler l'IA 3 fois ! (1 appel initial + 2 retries)
        verify(mockClient, times(3)).execute(any(Request.class), any(Request.Options.class));
    }
}
