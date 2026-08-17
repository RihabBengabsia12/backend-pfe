package tn.rihab.analysteservice.config;

import feign.Logger;
import feign.RequestInterceptor;
import feign.Request;
import feign.RetryableException;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.TimeUnit;

@Configuration
public class FeignConfig {

    @Value("${projectiq.internal-api-key:}")
    private String internalApiKey;

    /** Identifie les appels serveur-à-serveur vers project-service. */
    @Bean
    public RequestInterceptor internalServiceKeyInterceptor() {
        return request -> {
            if (internalApiKey != null && !internalApiKey.isBlank()) {
                request.header("X-Internal-Service-Key", internalApiKey);
            }
        };
    }

    @Bean
    public Request.Options feignOptions() {
        return new Request.Options(
                10, TimeUnit.SECONDS,    // connect timeout
                240, TimeUnit.SECONDS,   // read timeout — génération méthodologie peut être longue
                true
        );
    }

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    // --- TNF-05 : Résilience IA (Retry) ---
    @Bean
    public Retryer feignRetryer() {
        // Période initiale de 1s, délai max 3s, avec 3 tentatives au total
        return new Retryer.Default(1000, 3000, 3);
    }

    @Bean
    public ErrorDecoder feignErrorDecoder() {
        return (methodKey, response) -> {
            // Si l'IA est surchargée ou redémarre (502 Bad Gateway, 503 Service Unavailable)
            // On déclenche un Retry automatique
            if (response.status() == 502 || response.status() == 503 || response.status() == 429) {
                return new RetryableException(
                        response.status(),
                        "Service IA indisponible (" + response.status() + "), on retente...",
                        response.request().httpMethod(),
                        (java.util.Date) null,
                        response.request()
                );
            }
            if (response.status() == 504) {
                return new RuntimeException("Timeout sur " + methodKey + " — document trop volumineux ?");
            }
            return new RuntimeException("Erreur [" + response.status() + "] sur " + methodKey);
        };
    }
}
