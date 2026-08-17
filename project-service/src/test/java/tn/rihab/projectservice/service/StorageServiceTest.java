package tn.rihab.projectservice.service;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.rihab.projectservice.config.MinIOConfig;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
class StorageServiceTest {

    @Mock
    private MinioClient minioClient;

    @InjectMocks
    private StorageService storageService;

    // --- TEST NON-FONCTIONNEL : TNF-07 - Stockage MinIO ---

    @Test
    void TNF_07_01_Upload_Text_MinIO() throws Exception {
        String bucket = "projectiq-texte";
        String objectName = "1234/document_full.txt";
        String content = "Contenu extrait du TDR pour le test MinIO.";

        // Simulation : Le bucket n'existe pas, il doit être créé
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        // Appel du service
        String path = storageService.uploadText(bucket, objectName, content);

        // Vérifications
        assertEquals(bucket + "/" + objectName, path);

        // Vérification de la création du bucket
        verify(minioClient, times(1)).makeBucket(any(MakeBucketArgs.class));

        // Vérification de l'upload du fichier texte
        ArgumentCaptor<PutObjectArgs> putArgsCaptor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient, times(1)).putObject(putArgsCaptor.capture());

        PutObjectArgs capturedArgs = putArgsCaptor.getValue();
        assertEquals(bucket, capturedArgs.bucket());
        assertEquals(objectName, capturedArgs.object());
        assertEquals("text/plain; charset=utf-8", capturedArgs.contentType());
    }
}
