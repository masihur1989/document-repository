package com.docrepo.service;

import com.docrepo.exception.StorageException;
import io.minio.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock
    private MinioClient minioClient;

    @InjectMocks
    private StorageService storageService;

    private final String bucketName = "test-bucket";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(storageService, "bucketName", bucketName);
    }

    @Test
    void uploadFile_Success() throws Exception {
        InputStream inputStream = new ByteArrayInputStream("test content".getBytes());
        String contentType = "application/pdf";
        long size = 12L;
        String originalFilename = "test.pdf";

        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(null);

        String result = storageService.uploadFile(inputStream, contentType, size, originalFilename);

        assertNotNull(result);
        assertTrue(result.endsWith(".pdf"));
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void uploadFile_WithoutExtension() throws Exception {
        InputStream inputStream = new ByteArrayInputStream("test content".getBytes());
        String contentType = "application/octet-stream";
        long size = 12L;
        String originalFilename = "testfile";

        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(null);

        String result = storageService.uploadFile(inputStream, contentType, size, originalFilename);

        assertNotNull(result);
        assertFalse(result.contains("."));
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void uploadFile_ThrowsStorageException() throws Exception {
        InputStream inputStream = new ByteArrayInputStream("test content".getBytes());

        when(minioClient.putObject(any(PutObjectArgs.class)))
                .thenThrow(new RuntimeException("MinIO error"));

        assertThrows(StorageException.class, 
                () -> storageService.uploadFile(inputStream, "application/pdf", 12L, "test.pdf"));
    }

    @Test
    void downloadFile_Success() throws Exception {
        String storageKey = "test-key.pdf";
        GetObjectResponse mockResponse = mock(GetObjectResponse.class);

        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(mockResponse);

        InputStream result = storageService.downloadFile(storageKey);

        assertNotNull(result);
        verify(minioClient).getObject(any(GetObjectArgs.class));
    }

    @Test
    void downloadFile_ThrowsStorageException() throws Exception {
        String storageKey = "test-key.pdf";

        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenThrow(new RuntimeException("MinIO error"));

        assertThrows(StorageException.class, () -> storageService.downloadFile(storageKey));
    }

    @Test
    void deleteFile_Success() throws Exception {
        String storageKey = "test-key.pdf";

        doNothing().when(minioClient).removeObject(any(RemoveObjectArgs.class));

        storageService.deleteFile(storageKey);

        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void deleteFile_ThrowsStorageException() throws Exception {
        String storageKey = "test-key.pdf";

        doThrow(new RuntimeException("MinIO error"))
                .when(minioClient).removeObject(any(RemoveObjectArgs.class));

        assertThrows(StorageException.class, () -> storageService.deleteFile(storageKey));
    }

    @Test
    void getFileInfo_Success() throws Exception {
        String storageKey = "test-key.pdf";
        StatObjectResponse mockResponse = mock(StatObjectResponse.class);

        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(mockResponse);

        StatObjectResponse result = storageService.getFileInfo(storageKey);

        assertNotNull(result);
        verify(minioClient).statObject(any(StatObjectArgs.class));
    }

    @Test
    void getFileInfo_ThrowsStorageException() throws Exception {
        String storageKey = "test-key.pdf";

        when(minioClient.statObject(any(StatObjectArgs.class)))
                .thenThrow(new RuntimeException("MinIO error"));

        assertThrows(StorageException.class, () -> storageService.getFileInfo(storageKey));
    }
}
