package tn.rihab.authservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tn.rihab.authservice.DTO.UserSyncDTO;
import tn.rihab.authservice.DTO.EmailNotificationDTO;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    // --- Configuration Sync Utilisateur ---
    public static final String USER_QUEUE = "user.sync.queue";
    public static final String USER_EXCHANGE = "user.exchange"; // ✅ Ajouté
    public static final String USER_ROUTING_KEY = "user.sync.routing.key"; // ✅ Ajouté

    // --- Configuration Email Notification ---
    public static final String MAIL_QUEUE = "mail.queue";
    public static final String MAIL_EXCHANGE = "mail.exchange";
    public static final String MAIL_ROUTING_KEY = "mail.routing.key";

    // ---------------------------------------------------------
    // BEANS POUR LA SYNCHRO UTILISATEUR (Indispensable pour ton tableau)
    // ---------------------------------------------------------
    @Bean
    public Queue userSyncQueue() {
        return new Queue(USER_QUEUE, true);
    }

    @Bean
    public DirectExchange userExchange() {
        return new DirectExchange(USER_EXCHANGE);
    }

    @Bean
    public Binding userBinding(Queue userSyncQueue, DirectExchange userExchange) {
        return BindingBuilder.bind(userSyncQueue).to(userExchange).with(USER_ROUTING_KEY);
    }

    // ---------------------------------------------------------
    // BEANS POUR LES EMAILS
    // ---------------------------------------------------------
    @Bean
    public Queue mailQueue() {
        return new Queue(MAIL_QUEUE, true);
    }

    @Bean
    public DirectExchange mailExchange() {
        return new DirectExchange(MAIL_EXCHANGE);
    }

    @Bean
    public Binding mailBinding(Queue mailQueue, DirectExchange mailExchange) {
        return BindingBuilder.bind(mailQueue).to(mailExchange).with(MAIL_ROUTING_KEY);
    }

    // --- Configuration Pushover Notification ---
    public static final String NOTIFICATION_QUEUE = "notification.queue";
    public static final String NOTIFICATION_EXCHANGE = "notification.exchange";
    public static final String NOTIFICATION_ROUTING_KEY = "notification.routing.key";

    @Bean
    public Queue notificationQueue() {
        return new Queue(NOTIFICATION_QUEUE, true);
    }

    @Bean
    public DirectExchange notificationExchange() {
        return new DirectExchange(NOTIFICATION_EXCHANGE);
    }

    @Bean
    public Binding notificationBinding(Queue notificationQueue, DirectExchange notificationExchange) {
        return BindingBuilder.bind(notificationQueue).to(notificationExchange).with(NOTIFICATION_ROUTING_KEY);
    }

    // ---------------------------------------------------------
    // CONVERTISSEUR JSON (Pour les deux DTOs)
    // ---------------------------------------------------------
    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("*");

        Map<String, Class<?>> idClassMapping = new HashMap<>();
        // Important: Utiliser des clés simples pour que l'autre service reconnaisse l'objet
        idClassMapping.put("UserSyncDTO", UserSyncDTO.class);
        idClassMapping.put("EmailNotificationDTO", EmailNotificationDTO.class);

        typeMapper.setIdClassMapping(idClassMapping);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}