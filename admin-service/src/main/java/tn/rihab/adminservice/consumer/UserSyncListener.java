package tn.rihab.adminservice.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import tn.rihab.adminservice.DTO.UserSyncDTO;
import tn.rihab.adminservice.service.UserService;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSyncListener {

    private final UserService userService;

    @RabbitListener(queues = "user.sync.queue")
    public void handleUserSync(Object message) {
        try {
            if (message instanceof UserSyncDTO syncDto) {
                // Cas 1 : Création/Synchronisation classique
                log.info("📥 Synchro DTO reçue -> Email: {}, Role: {}", syncDto.getEmail(), syncDto.getRole());
                userService.syncUser(syncDto);
            }
            else if (message instanceof Map) {
                // Cas 2 : Notification de rejet (REJECTED)
                Map<String, String> data = (Map<String, String>) message;
                String email = data.get("email");
                String status = data.get("status");

                log.info("📥 Notification de statut reçue -> Email: {}, Status: {}", email, status);

                if ("REJECTED".equals(status)) {
                    userService.updateStatusToRejected(email);
                }
            }
            log.info("✅ Opération de synchronisation terminée avec succès");
        } catch (Exception e) {
            log.error("❌ ERREUR lors du traitement du message RabbitMQ : ", e);
        }
    }
}