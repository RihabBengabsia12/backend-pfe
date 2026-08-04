package tn.rihab.authservice.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tn.rihab.authservice.entity.AccessEvent;
import tn.rihab.authservice.repository.AccessEventRepository;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final AccessEventRepository eventRepo;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAudit(UUID userId, String email, String type, String res, String desc, HttpServletRequest http) {
        try {
            AccessEvent event = AccessEvent.builder()
                    .userId(userId)
                    .emailAttempted(email)
                    .eventType(type)
                    .result(res)
                    .ipAddress(http != null ? http.getRemoteAddr() : "0.0.0.0")
                    .userAgent(desc != null ? desc : "Action système")
                    .occurredAt(OffsetDateTime.now())
                    .build();
            eventRepo.save(event);
        } catch (Exception e) {
            // Si l'audit échoue, on log l'erreur mais on ne bloque pas l'utilisateur !
            System.err.println("⚠️ Erreur audit ignorée : " + e.getMessage());
        }
    }
}