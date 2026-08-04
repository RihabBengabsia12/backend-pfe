package tn.rihab.analysteservice.service;

import io.minio.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

/**
 * Bridge MinIO pour analyste-service.
 * Stocke les fichiers générés (DOCX, ZIP) sur MinIO.
 * Miroir simplifié du StorageService de project-service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PackStorageService {

    private final MinioClient minioClient;

    /**
     * Upload des octets vers un bucket MinIO spécifique.
     */
    public String uploadBytes(String bucket, String objectName, byte[] data, String contentType) {
        try {
            ensureBucket(bucket);
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .contentType(contentType)
                    .build());
            log.info("[Storage] Uploadé : {}/{} ({} Ko)", bucket, objectName, data.length / 1024);
            return bucket + "/" + objectName;
        } catch (Exception e) {
            throw new RuntimeException("Upload MinIO échoué : " + objectName, e);
        }
    }

    /**
     * Télécharge un tableau d'octets depuis un chemin complet "bucket/objectName".
     */
    public byte[] downloadBytes(String fullPath) {
        String[] p = split(fullPath);
        try (var stream = minioClient.getObject(
                GetObjectArgs.builder().bucket(p[0]).object(p[1]).build())) {
            return stream.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("Download MinIO échoué : " + fullPath, e);
        }
    }

    /**
     * S'assure de l'existence du bucket, le crée si nécessaire.
     */
    private void ensureBucket(String bucket) {
        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("[Storage] Bucket créé dynamiquement : {}", bucket);
            }
        } catch (Exception e) {
            throw new RuntimeException("Création bucket échouée : " + bucket, e);
        }
    }

    /**
     * Découpe de manière sécurisée le chemin d'accès complet.
     */
    private String[] split(String path) {
        if (path == null || !path.contains("/")) {
            throw new IllegalArgumentException("Le format du chemin d'accès MinIO est incorrect (attendu: 'bucket/nom-objet') : " + path);
        }
        int i = path.indexOf('/');
        return new String[]{path.substring(0, i), path.substring(i + 1)};
    }
}