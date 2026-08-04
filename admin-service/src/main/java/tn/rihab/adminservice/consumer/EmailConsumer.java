package tn.rihab.adminservice.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import tn.rihab.adminservice.DTO.EmailNotificationDTO;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailConsumer {

    private final JavaMailSender mailSender;

    // Cette méthode écoute la queue définie dans l'Auth-Service
    @RabbitListener(queues = "mail.queue")
    public void receiveAndSendEmail(EmailNotificationDTO notification) {
        log.info("📩 Message reçu pour l'envoi d'un email à : {}", notification.getTo());

        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setTo(notification.getTo());
            mail.setSubject(notification.getSubject());
            mail.setText(notification.getMessage());
            mail.setFrom("dsi@projectiq.tn"); // Nom pro pour ton PFE

            mailSender.send(mail);
            log.info("✅ Email envoyé avec succès !");
        } catch (Exception e) {
            log.error("❌ Erreur lors de l'envoi de l'email : {}", e.getMessage());
        }
    }
}