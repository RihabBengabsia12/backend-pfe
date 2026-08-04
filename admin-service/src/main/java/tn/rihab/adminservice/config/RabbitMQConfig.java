package tn.rihab.adminservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tn.rihab.adminservice.DTO.UserSyncDTO;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    public static final String QUEUE_NAME = "user.sync.queue";
    public static final String EXCHANGE_NAME = "user.exchange"; // Doit être identique à l'Auth
    public static final String ROUTING_KEY = "user.sync.routing.key"; // Doit être identique à l'Auth

    @Bean
    public Queue userSyncQueue() {
        return new Queue(QUEUE_NAME, true);
    }

    // --- LE RACCORDEMENT MANQUANT ---
    @Bean
    public DirectExchange userExchange() {
        return new DirectExchange(EXCHANGE_NAME);
    }

    @Bean
    public Binding userBinding(Queue userSyncQueue, DirectExchange userExchange) {
        return BindingBuilder.bind(userSyncQueue)
                .to(userExchange)
                .with(ROUTING_KEY); // C'est ici que la tuyauterie est scellée
    }
    // --------------------------------

    @Bean
    public Queue mailQueue() {
        return new Queue("mail.queue", true);
    }

    @Bean
    public Jackson2JsonMessageConverter jsonMessageConverter() {
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("*");

        Map<String, Class<?>> idClassMapping = new HashMap<>();
        // On mappe le nom envoyé par l'Auth vers la classe locale de l'Admin
        idClassMapping.put("tn.rihab.authservice.DTO.UserSyncDTO", UserSyncDTO.class);
        idClassMapping.put("UserSyncDTO", UserSyncDTO.class);

        typeMapper.setIdClassMapping(idClassMapping);

        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setJavaTypeMapper(typeMapper);

        return converter;
    }
}