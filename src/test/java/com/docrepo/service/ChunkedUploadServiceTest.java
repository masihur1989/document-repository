package com.docrepo.service;

import com.docrepo.dto.*;
import com.docrepo.exception.StorageException;
import com.docrepo.model.Document;
import com.docrepo.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChunkedUploadServiceTest {

    @Mock
    private StorageService storageService;

    @Mock
    private DocumentRepository documentRepository;

    @TempDir
    Path tempDir;

    private ChunkedUploadService chunkedUploadService;

    @BeforeEach
    void setUp() {
        chunkedUploadService = new ChunkedUploadService(
                storageService,
                documentRepository,
                tempDir.toString()
        );
    }

    @Test
    void initUpload_shouldCreateSession() {
        ChunkedUploadInitRequest request = ChunkedUploadInitRequest.builder()
                .filename("test.pdf")
                .contentType("application/pdf")
                .fileSize(25 * 1024 * 1024L)
                .build();

        ChunkedUploadInitResponse response = chunkedUploadService.initUpload(
                request, "user123", "testuser");

        assertNotNull(response.getUploadId());
        assertEquals(3, response.getTotalChunks());
        assertTrue(response.getChunkSize() > 0);
        assertNotNull(response.getExpiresAt());
    }

    @Test
    void uploadChunk_shouldSaveChunk() {
        ChunkedUploadInitRequest request = ChunkedUploadInitRequest.builder()
                .filename("test.pdf")
                .contentType("application/pdf")
                .fileSize(15 * 1024 * 1024L)
                .build();

        ChunkedUploadInitResponse initResponse = chunkedUploadService.initUpload(
                request, "user123", "testuser");

        MockMultipartFile chunk = new MockMultipartFile(
                "chunk", "chunk-0", "application/octet-stream", 
                new byte[1024]);

        ChunkUploadResponse response = chunkedUploadService.uploadChunk(
                initResponse.getUploadId(), 0, chunk);

        assertEquals(0, response.getChunkIndex());
        assertEquals(1, response.getCompletedChunks());
        assertTrue(response.getUploadedChunks().contains(0));
    }

    @Test
    void uploadChunk_shouldFailForInvalidSession() {
        MockMultipartFile chunk = new MockMultipartFile(
                "chunk", "chunk-0", "application/octet-stream", 
                new byte[1024]);

        assertThrows(StorageException.class, () -> 
                chunkedUploadService.uploadChunk("invalid-id", 0, chunk));
    }

    @Test
    void uploadChunk_shouldFailForInvalidChunkIndex() {
        ChunkedUploadInitRequest request = ChunkedUploadInitRequest.builder()
                .filename("test.pdf")
                .contentType("application/pdf")
                .fileSize(15 * 1024 * 1024L)
                .build();

        ChunkedUploadInitResponse initResponse = chunkedUploadService.initUpload(
                request, "user123", "testuser");

        MockMultipartFile chunk = new MockMultipartFile(
                "chunk", "chunk-0", "application/octet-stream", 
                new byte[1024]);

        assertThrows(StorageException.class, () -> 
                chunkedUploadService.uploadChunk(initResponse.getUploadId(), 100, chunk));
    }

    @Test
    void getStatus_shouldReturnSessionStatus() {
        ChunkedUploadInitRequest request = ChunkedUploadInitRequest.builder()
                .filename("test.pdf")
                .contentType("application/pdf")
                .fileSize(15 * 1024 * 1024L)
                .build();

        ChunkedUploadInitResponse initResponse = chunkedUploadService.initUpload(
                request, "user123", "testuser");

        ChunkedUploadStatusResponse status = chunkedUploadService.getStatus(
                initResponse.getUploadId());

        assertEquals(initResponse.getUploadId(), status.getUploadId());
        assertEquals("test.pdf", status.getFilename());
        assertEquals(0, status.getCompletedChunks());
        assertEquals("IN_PROGRESS", status.getStatus());
    }

    @Test
    void getStatus_shouldFailForInvalidSession() {
        assertThrows(StorageException.class, () -> 
                chunkedUploadService.getStatus("invalid-id"));
    }

    @Test
    void completeUpload_shouldFailIfNotComplete() {
        ChunkedUploadInitRequest request = ChunkedUploadInitRequest.builder()
                .filename("test.pdf")
                .contentType("application/pdf")
                .fileSize(15 * 1024 * 1024L)
                .build();

        ChunkedUploadInitResponse initResponse = chunkedUploadService.initUpload(
                request, "user123", "testuser");

        assertThrows(StorageException.class, () -> 
                chunkedUploadService.completeUpload(initResponse.getUploadId(), null));
    }

    @Test
    void completeUpload_shouldSucceedWhenAllChunksUploaded() throws Exception {
        ChunkedUploadInitRequest request = ChunkedUploadInitRequest.builder()
                .filename("test.txt")
                .contentType("text/plain")
                .fileSize(1024L)
                .build();

        ChunkedUploadInitResponse initResponse = chunkedUploadService.initUpload(
                request, "user123", "testuser");

        MockMultipartFile chunk = new MockMultipartFile(
                "chunk", "chunk-0", "application/octet-stream", 
                new byte[1024]);

        chunkedUploadService.uploadChunk(initResponse.getUploadId(), 0, chunk);

        when(storageService.uploadFile(any(), eq("text/plain"), eq(1024L), eq("test.txt")))
                .thenReturn("storage-key-123");

        Document savedDoc = Document.builder()
                .id("doc123")
                .filename("test.txt")
                .originalFilename("test.txt")
                .contentType("text/plain")
                .size(1024L)
                .storageKey("storage-key-123")
                .ownerId("user123")
                .ownerUsername("testuser")
                .build();

        when(documentRepository.save(any(Document.class))).thenReturn(savedDoc);

        DocumentDTO result = chunkedUploadService.completeUpload(
                initResponse.getUploadId(), null);

        assertNotNull(result);
        assertEquals("doc123", result.getId());
        assertEquals("test.txt", result.getFilename());
    }

    @Test
    void cancelUpload_shouldRemoveSession() {
        ChunkedUploadInitRequest request = ChunkedUploadInitRequest.builder()
                .filename("test.pdf")
                .contentType("application/pdf")
                .fileSize(15 * 1024 * 1024L)
                .build();

        ChunkedUploadInitResponse initResponse = chunkedUploadService.initUpload(
                request, "user123", "testuser");

        chunkedUploadService.cancelUpload(initResponse.getUploadId());

        assertThrows(StorageException.class, () -> 
                chunkedUploadService.getStatus(initResponse.getUploadId()));
    }

    @Test
    void cancelUpload_shouldFailForInvalidSession() {
        assertThrows(StorageException.class, () -> 
                chunkedUploadService.cancelUpload("invalid-id"));
    }
}
