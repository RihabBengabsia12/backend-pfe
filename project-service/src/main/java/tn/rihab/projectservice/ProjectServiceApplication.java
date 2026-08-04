package tn.rihab.projectservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration; // Import nécessaire
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// Excluez la configuration automatique ici :
@SpringBootApplication(
        scanBasePackages = "tn.rihab.projectservice",
        exclude = { SecurityAutoConfiguration.class }
)
@EnableFeignClients
@EnableAsync
@EnableScheduling
@Import(tn.rihab.projectservice.config.SecurityConfig.class)
public class ProjectServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProjectServiceApplication.class, args);
    }
}