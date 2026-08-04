package tn.rihab.projectservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class RabbitMQConfig {

    // ── Exchanges ───────────────────────────────────────────────────────────
    public static final String EXCHANGE_DOSSIER  = "projectiq.dossier";
    public static final String EXCHANGE_ANALYSTE = "projectiq.analyste";

    // ── Routing keys publiées par project-service ───────────────────────────
    public static final String RK_DOSSIER_P1_EXTRACTED = "dossier.p1.extracted";
    public static final String RK_DOSSIER_INDEXED    = "dossier.indexed";
    public static final String RK_DOSSIER_SUBMITTED  = "dossier.submitted";

    // ── Routing keys consommées depuis analyste-service ─────────────────────
    public static final String RK_SCORING_COMPLETED  = "scoring.completed";
    public static final String RK_MATCHING_COMPLETED = "matching.completed";
    public static final String RK_APO_GENERATED      = "apo.generated";
    public static final String RK_AUDIT_GENERATED    = "audit.generated";

    // ── Noms des queues ─────────────────────────────────────────────────────
    public static final String Q_DOSSIER_P1_EXTRACTED = "q.dossier.p1.extracted";
    public static final String Q_DOSSIER_INDEXED    = "q.dossier.indexed";
    public static final String Q_DOSSIER_SUBMITTED  = "q.dossier.submitted";
    public static final String Q_SCORING_COMPLETED  = "q.scoring.completed";
    public static final String Q_MATCHING_COMPLETED = "q.matching.completed";
    public static final String Q_APO_GENERATED      = "q.apo.generated";
    public static final String Q_AUDIT_GENERATED    = "q.audit.generated";

    public static final String RK_FORCE_COMPATIBLE = "matching.force_compatible";
    public static final String Q_FORCE_COMPATIBLE  = "q.force_compatible";

    // ── Exchanges beans ─────────────────────────────────────────────────────
    @Bean
    public TopicExchange dossierExchange() {
        return new TopicExchange(EXCHANGE_DOSSIER, true, false);
    }

    @Bean
    public TopicExchange analysteExchange() {
        return new TopicExchange(EXCHANGE_ANALYSTE, true, false);
    }

    // ── Queues beans ─────────────────────────────────────────────────────────
    @Bean public Queue qDossierP1Extracted(){ return QueueBuilder.durable(Q_DOSSIER_P1_EXTRACTED).build(); }
    @Bean public Queue qDossierIndexed()    { return QueueBuilder.durable(Q_DOSSIER_INDEXED).build(); }
    @Bean public Queue qDossierSubmitted()  { return QueueBuilder.durable(Q_DOSSIER_SUBMITTED).build(); }
    @Bean public Queue qScoringCompleted()  { return QueueBuilder.durable(Q_SCORING_COMPLETED).build(); }
    @Bean public Queue qMatchingCompleted() { return QueueBuilder.durable(Q_MATCHING_COMPLETED).build(); }
    @Bean public Queue qApoGenerated()      { return QueueBuilder.durable(Q_APO_GENERATED).build(); }
    @Bean public Queue qAuditGenerated()    { return QueueBuilder.durable(Q_AUDIT_GENERATED).build(); }

    // ── Bindings ─────────────────────────────────────────────────────────────
    @Bean
    public Binding bindingDossierP1Extracted() {
        return BindingBuilder.bind(qDossierP1Extracted())
                .to(dossierExchange()).with(RK_DOSSIER_P1_EXTRACTED);
    }

    @Bean
    public Binding bindingDossierIndexed() {
        return BindingBuilder.bind(qDossierIndexed())
                .to(dossierExchange()).with(RK_DOSSIER_INDEXED);
    }

    @Bean
    public Binding bindingDossierSubmitted() {
        return BindingBuilder.bind(qDossierSubmitted())
                .to(dossierExchange()).with(RK_DOSSIER_SUBMITTED);
    }

    @Bean
    public Binding bindingScoringCompleted() {
        return BindingBuilder.bind(qScoringCompleted())
                .to(analysteExchange()).with(RK_SCORING_COMPLETED);
    }

    @Bean
    public Binding bindingMatchingCompleted() {
        return BindingBuilder.bind(qMatchingCompleted())
                .to(analysteExchange()).with(RK_MATCHING_COMPLETED);
    }

    @Bean
    public Binding bindingApoGenerated() {
        return BindingBuilder.bind(qApoGenerated())
                .to(analysteExchange()).with(RK_APO_GENERATED);
    }

    @Bean
    public Binding bindingAuditGenerated() {
        return BindingBuilder.bind(qAuditGenerated())
                .to(analysteExchange()).with(RK_AUDIT_GENERATED);
    }

    // ── Convertisseur JSON ────────────────────────────────────────────────────
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf) {
        RabbitTemplate template = new RabbitTemplate(cf);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
    @Bean
    public Queue qForceCompatible() {
        return QueueBuilder.durable(Q_FORCE_COMPATIBLE).build();
    }

    @Bean
    public Binding bindingForceCompatible() {
        return BindingBuilder.bind(qForceCompatible())
                .to(analysteExchange()).with(RK_FORCE_COMPATIBLE);
    }
}