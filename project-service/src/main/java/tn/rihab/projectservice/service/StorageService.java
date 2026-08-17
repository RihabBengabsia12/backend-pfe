package tn.rihab.projectservice.service;

import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tn.rihab.projectservice.config.MinIOConfig;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;


@Service
@RequiredArgsConstructor
@Slf4j
public class StorageService {

    private final MinioClient minioClient;
    @Qualifier("publicMinioClient")
    private final MinioClient publicMinioClient;


    public String uploadFile(String bucket, String objectName, MultipartFile file) {
        try {
            ensureBucketExists(bucket);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType() != null
                                    ? file.getContentType() : "application/octet-stream")
                            .build()
            );
            log.info("[MinIO] Fichier uploadé : {}/{}", bucket, objectName);
            return bucket + "/" + objectName;
        } catch (Exception e) {
            log.error("[MinIO] Erreur upload fichier {}/{}: {}", bucket, objectName, e.getMessage(), e);
            throw new RuntimeException("Upload MinIO échoué : " + objectName, e);
        }
    }


    public String uploadText(String bucket, String objectName, String content) {
        try {
            ensureBucketExists(bucket);
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                            .contentType("text/plain; charset=utf-8")
                            .build()
            );
            log.info("[MinIO] Texte uploadé : {}/{} ({} chars)", bucket, objectName, content.length());
            return bucket + "/" + objectName;
        } catch (Exception e) {
            log.error("[MinIO] Erreur upload texte {}/{}: {}", bucket, objectName, e.getMessage(), e);
            throw new RuntimeException("Upload texte MinIO échoué : " + objectName, e);
        }
    }

    public String uploadBytes(String bucket, String objectName, byte[] data, String contentType) {
        try {
            ensureBucketExists(bucket);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .stream(new ByteArrayInputStream(data), data.length, -1)
                            .contentType(contentType)
                            .build()
            );
            log.info("[MinIO] Bytes uploadés : {}/{} ({} Ko)", bucket, objectName, data.length / 1024);
            return bucket + "/" + objectName;
        } catch (Exception e) {
            throw new RuntimeException("Upload bytes MinIO échoué : " + objectName, e);
        }
    }


    public String downloadText(String fullPath) {
        String[] parts = splitPath(fullPath);
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(parts[0])
                        .object(parts[1])
                        .build())) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[MinIO] Erreur lecture texte {}: {}", fullPath, e.getMessage(), e);
            throw new RuntimeException("Lecture MinIO échouée : " + fullPath, e);
        }
    }


    public InputStream getStream(String fullPath) {
        String[] parts = splitPath(fullPath);
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(parts[0])
                            .object(parts[1])
                            .build());
        } catch (Exception e) {
            throw new RuntimeException("Lecture stream MinIO échouée : " + fullPath, e);
        }
    }


    public String getPresignedUrl(String fullPath) {
        String[] parts = splitPath(fullPath);
        try {
            // L'URL est ouverte par le navigateur : elle doit donc utiliser
            // localhost (ou le domaine public), jamais le nom Docker « minio ».
            return publicMinioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .bucket(parts[0])
                            .object(parts[1])
                            .method(Method.GET)
                            .expiry(7, TimeUnit.DAYS)
                            .build());
        } catch (Exception e) {
            throw new RuntimeException("Génération URL présignée échouée : " + fullPath, e);
        }
    }


    public boolean exists(String fullPath) {
        String[] parts = splitPath(fullPath);
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(parts[0])
                            .object(parts[1])
                            .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }


    private void ensureBucketExists(String bucket) {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("[MinIO] Bucket créé : {}", bucket);
            }
        } catch (Exception e) {
            throw new RuntimeException("Création bucket MinIO échouée : " + bucket, e);
        }
    }


    private String[] splitPath(String fullPath) {
        int idx = fullPath.indexOf('/');
        if (idx < 0) throw new IllegalArgumentException("Chemin MinIO invalide : " + fullPath);
        return new String[]{fullPath.substring(0, idx), fullPath.substring(idx + 1)};
    }
}
