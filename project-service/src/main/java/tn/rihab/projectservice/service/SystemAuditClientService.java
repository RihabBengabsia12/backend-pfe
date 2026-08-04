package tn.rihab.projectservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@Slf4j
public class SystemAuditClientService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String ANALYSTE_SERVICE_URL = "http://localhost:8081/api/system-audit"; // Hardcoded for demo/simplicity

    public void logSystemAction(String action, String detail) {
        try {
            Map<String, String> payload = Map.of(
                    "actorEmail", "admin@st2i.com.tn",
                    "action", action,
                    "detail", detail
            );
            restTemplate.postForObject(ANALYSTE_SERVICE_URL, payload, String.class);
            log.info("[SystemAudit] Action '{}' envoyée à analyste-service", action);
        } catch (Exception e) {
            log.error("[SystemAudit] Impossible d'envoyer l'audit système : {}", e.getMessage());
        }
    }
}
