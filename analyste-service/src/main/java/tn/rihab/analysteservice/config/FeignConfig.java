package tn.rihab.analysteservice.config;

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
                10, TimeUnit.SECONDS,    // connect timeout
                240, TimeUnit.SECONDS,   // read timeout — génération méthodologie peut être longue
                true
        );
    }

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    @Bean
    public ErrorDecoder feignErrorDecoder() {
        return (methodKey, response) -> {
            if (response.status() == 503) {
                return new RuntimeException("Service distant indisponible — " + methodKey);
            }
            if (response.status() == 504) {
                return new RuntimeException("Timeout sur " + methodKey + " — document trop volumineux ?");
            }
            return new RuntimeException("Erreur [" + response.status() + "] sur " + methodKey);
        };
    }
}