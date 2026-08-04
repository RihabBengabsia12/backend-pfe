package tn.rihab.projectservice.config;

import feign.Logger;
import feign.Request;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;


@Configuration
public class FeignConfig {

    @Bean
    public Request.Options feignOptions() {
        return new Request.Options(
                10, TimeUnit.SECONDS,   // connect timeout
                180, TimeUnit.SECONDS,  // read timeout — important pour Claude API
                true                    // followRedirects
        );
    }

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC; // FULL en dev pour voir les payloads
    }

    @Bean
    public ErrorDecoder feignErrorDecoder() {
        return (methodKey, response) -> {
            if (response.status() == 503) {
                return new RuntimeException(
                        "ia-service indisponible — réessayez dans quelques instants");
            }
            if (response.status() == 504) {
                return new RuntimeException(
                        "Timeout ia-service — le document est peut-être trop volumineux");
            }
            return new RuntimeException(
                    "Erreur ia-service [" + response.status() + "] sur " + methodKey);
        };
    }
}