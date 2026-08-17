package tn.rihab.projectservice.config;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.MethodName.class)
class EurekaRegistrationTest {

    // --- TEST NON-FONCTIONNEL : TNF-08 - Enregistrement Eureka (Disponibilité) ---

    @Test
    void TNF_08_01_Configuration_Eureka_Active() {
        // Chargement du fichier application.yml sans démarrer tout le contexte Spring (plus rapide et fiable)
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));
        Properties properties = factory.getObject();

        assertNotNull(properties, "Les propriétés de l'application doivent être chargées");

        // Vérification 1 : L'URL du serveur Eureka est bien configurée
        String defaultZone = properties.getProperty("eureka.client.service-url.defaultZone");
        assertNotNull(defaultZone, "L'URL par défaut du serveur Eureka doit être définie");
        assertTrue(defaultZone.contains("8761/eureka"), "L'URL doit pointer vers le port 8761 d'Eureka");

        // Vérification 2 : La préférence IP est activée
        String preferIp = properties.getProperty("eureka.instance.prefer-ip-address");
        assertEquals("true", preferIp, "L'instance doit préférer l'enregistrement par IP");

        // Vérification 3 : Le nom de l'application est bien défini pour l'enregistrement
        String appName = properties.getProperty("spring.application.name");
        assertEquals("PROJECT-SERVICE", appName, "Le nom de l'application doit être défini pour l'identification sur Eureka");
    }
}
