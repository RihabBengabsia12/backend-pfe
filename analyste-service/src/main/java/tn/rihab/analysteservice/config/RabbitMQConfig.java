package tn.rihab.analysteservice.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration centrale de RabbitMQ.
 * Version Senior : Déclaration complète et sécurisée du réseau d'échanges
 * pour garantir l'idempotence et éviter les pertes de messages au démarrage.
 */
@Configuration
public class RabbitMQConfig {

    // ── Exchanges ────────────────────────────────────────────────────────────
    public static final String EXCHANGE_DOSSIER  = "projectiq.dossier";
    public static final String EXCHANGE_ANALYSTE = "projectiq.analyste";

    // ── Routing Keys ─────────────────────────────────────────────────────────
    public static final String RK_DOSSIER_INDEXED    = "dossier.indexed";
    public static final String RK_DOSSIER_SUBMITTED  = "dossier.submitted";

    public static final String RK_SCORING_COMPLETED  = "scoring.completed";
    public static final String RK_MATCHING_COMPLETED = "matching.completed";
    public static final String RK_APO_GENERATED      = "apo.generated";
    public static final String RK_AUDIT_GENERATED    = "audit.generated";

    // ── Queues Consommées par analyste-service ───────────────────────────────
    public static final String Q_DOSSIER_INDEXED   = "q.analyste.dossier.indexed";
    public static final String Q_DOSSIER_SUBMITTED = "q.analyste.dossier.submitted";

    // ── 🚀 AJOUT SENIOR : Queues Consommées par project-service ────────────────
    // Déclarer ces noms ici sécurise tes envois depuis le Publisher
    public static final String Q_PROJECT_SCORING  = "q.project.scoring.completed";
    public static final String Q_PROJECT_MATCHING = "q.project.matching.completed";
    public static final String Q_PROJECT_APO      = "q.project.apo.generated";
    public static final String Q_PROJECT_AUDIT    = "q.project.audit.generated";

    public static final String RK_FORCE_COMPATIBLE = "matching.force_compatible";

    @Bean
    public TopicExchange dossierExchange() {
        return new TopicExchange(EXCHANGE_DOSSIER, true, false);
    }

    @Bean
    public TopicExchange analysteExchange() {
        return new TopicExchange(EXCHANGE_ANALYSTE, true, false);
    }

    // ── Configuration des Queues de l'Analyste ───────────────────────────────
    @Bean
    public Queue qDossierIndexed() { return QueueBuilder.durable(Q_DOSSIER_INDEXED).build(); }

    @Bean
    public Queue qDossierSubmitted() { return QueueBuilder.durable(Q_DOSSIER_SUBMITTED).build(); }

    @Bean
    public Binding bindingDossierIndexed() {
        return BindingBuilder.bind(qDossierIndexed()).to(dossierExchange()).with(RK_DOSSIER_INDEXED);
    }

    @Bean
    public Binding bindingDossierSubmitted() {
        return BindingBuilder.bind(qDossierSubmitted()).to(dossierExchange()).with(RK_DOSSIER_SUBMITTED);
    }

    // ── 🚀 AJOUT SENIOR : Configuration des Queues du Projet (Garantie de non-perte) ──
    @Bean
    public Queue qProjectScoring() { return QueueBuilder.durable(Q_PROJECT_SCORING).build(); }

    @Bean
    public Queue qProjectMatching() { return QueueBuilder.durable(Q_PROJECT_MATCHING).build(); }

    @Bean
    public Queue qProjectApo() { return QueueBuilder.durable(Q_PROJECT_APO).build(); }

    @Bean
    public Queue qProjectAudit() { return QueueBuilder.durable(Q_PROJECT_AUDIT).build(); }

    @Bean
    public Binding bindingProjectScoring() {
        return BindingBuilder.bind(qProjectScoring()).to(analysteExchange()).with(RK_SCORING_COMPLETED);
    }

    @Bean
    public Binding bindingProjectMatching() {
        return BindingBuilder.bind(qProjectMatching()).to(analysteExchange()).with(RK_MATCHING_COMPLETED);
    }

    @Bean
    public Binding bindingProjectApo() {
        return BindingBuilder.bind(qProjectApo()).to(analysteExchange()).with(RK_APO_GENERATED);
    }

    @Bean
    public Binding bindingProjectAudit() {
        return BindingBuilder.bind(qProjectAudit()).to(analysteExchange()).with(RK_AUDIT_GENERATED);
    }

    // ── Convertisseur JSON & Template ────────────────────────────────────────
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
}