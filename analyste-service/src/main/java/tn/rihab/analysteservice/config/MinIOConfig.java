package tn.rihab.analysteservice.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinIOConfig {

    // On utilise les noms de variables d'environnement (syntaxe Spring Boot)
    // S'il ne trouve pas la variable d'environnement, il utilise la valeur après le ":"
    @Value("${MINIO_ENDPOINT:http://projectiq-minio:9000}")
    private String endpoint;

    @Value("${MINIO_ACCESS_KEY:minioadmin}")
    private String accessKey;

    @Value("${MINIO_SECRET_KEY:minioadmin}")
    private String secretKey;

    @Bean
    public MinioClient minioClient() {
        // Log pour confirmer le point de terminaison utilisé lors du démarrage
        System.out.println("DEBUG - Initialisation MinIO avec endpoint : " + endpoint);

        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    public static final String BUCKET_ORIGINAUX    = "documents-originaux";
    public static final String BUCKET_TEXTE        = "documents-texte";
    public static final String BUCKET_APO          = "apo-generees";
    public static final String BUCKET_PACKS        = "packs-soumission";
    public static final String BUCKET_AUDIT        = "rapports-audit";
    public static final String BUCKET_NOGO         = "nogo-reports";
}