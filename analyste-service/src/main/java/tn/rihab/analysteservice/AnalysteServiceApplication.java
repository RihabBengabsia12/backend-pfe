package tn.rihab.analysteservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients // Maintenant reconnu grâce à l'importation
public class AnalysteServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalysteServiceApplication.class, args);
    }
}