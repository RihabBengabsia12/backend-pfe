package tn.rihab.authservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import tn.rihab.authservice.config.SecurityConfig;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@EnableDiscoveryClient
@SpringBootApplication
@Import(SecurityConfig.class)
// FORCER LE SCAN DES REPOSITORIES
@EnableJpaRepositories(basePackages = "tn.rihab.authservice.repository")
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
// FORCER LE SCAN DES ENTITÉS
@EntityScan(basePackages = "tn.rihab.authservice.entity")
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}