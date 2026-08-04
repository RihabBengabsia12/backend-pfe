package tn.rihab.authservice.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import tn.rihab.authservice.DTO.EmailNotificationDTO;
import tn.rihab.authservice.config.RabbitMQConfig;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailConsumer {

    private final JavaMailSender mailSender;

    // Récupération des clés depuis ton fichier .env pour la sécurité
    @Value("${PUSHOVER_TOKEN}")
    private String pushoverToken;

    @Value("${PUSHOVER_USER}")
    private String pushoverUser;

    /**
     * Cette méthode écoute la file 'mail.queue'.
     * Elle envoie l'email à Mailtrap ET la notification à ton téléphone.
     */
    @RabbitListener(queues = RabbitMQConfig.MAIL_QUEUE)
    public void consumeEmailMessage(EmailNotificationDTO notification) {
        log.info("📩 Message RabbitMQ reçu pour : {}", notification.getTo());

        try {
            // --- 1. ENVOI DE L'EMAIL (MAILTRAP) ---
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setTo(notification.getTo());
            mail.setSubject(notification.getSubject());
            mail.setText(notification.getMessage());
            mail.setFrom("no-reply@projectiq.tn");

            mailSender.send(mail);
            log.info("✅ Mailtrap : Email envoyé avec succès à {}", notification.getTo());

            // --- 2. ENVOI DE LA NOTIFICATION PUSHOVER (TÉLÉPHONE) ---
            sendPushoverNotification(notification);

        } catch (Exception e) {
            log.error("❌ Erreur lors du traitement du message : {}", e.getMessage());
        }
    }

    /**
     * Méthode privée pour gérer l'appel à l'API Pushover
     */
    private void sendPushoverNotification(EmailNotificationDTO dto) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String url = "https://api.pushover.net/1/messages.json";

            // Préparation des paramètres pour Pushover
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("token", pushoverToken);
            body.add("user", pushoverUser);
            body.add("title", "ProjectIQ : " + dto.getSubject());
            body.add("message", "Email envoyé à : " + dto.getTo() + "\nContenu : " + dto.getMessage());
            body.add("priority", "1"); // Fait vibrer le téléphone

            restTemplate.postForEntity(url, body, String.class);
            log.info("📱 Pushover : Notification envoyée sur ton téléphone !");

        } catch (Exception e) {
            // On utilise un try-catch séparé pour que si Pushover échoue,
            // cela n'annule pas le succès de l'envoi de l'email.
            log.warn("⚠️ Pushover n'a pas pu être envoyé : {}", e.getMessage());
        }
    }
}