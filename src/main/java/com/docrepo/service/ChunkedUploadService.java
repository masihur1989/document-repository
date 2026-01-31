package com.docrepo.service;

import com.docrepo.dto.*;
import com.docrepo.exception.StorageException;
import com.docrepo.model.ChunkedUploadSession;
import com.docrepo.model.Document;
import com.docrepo.repository.DocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ChunkedUploadService {

    private final Map<String, ChunkedUploadSession> sessions = new ConcurrentHashMap<>();
    private final StorageService storageService;
    private final DocumentRepository documentRepository;
    private final Path tempDir;

    @Value("${chunked-upload.chunk-size:10485760}")
    private long chunkSize = 10 * 1024 * 1024; // 10MB default

    @Value("${chunked-upload.session-timeout-hours:24}")
    private int sessionTimeoutHours = 24;

    public ChunkedUploadService(StorageService storageService, 
                                DocumentRepository documentRepository,
                                @Value("${chunked-upload.temp-dir:#{systemProperties['java.io.tmpdir']}/chunked-uploads}") String tempDirPath) {
        this.storageService = storageService;
        this.documentRepository = documentRepository;
        this.tempDir = Path.of(tempDirPath);
        
        try {
            Files.createDirectories(this.tempDir);
            log.info("Chunked upload temp directory: {}", this.tempDir);
        } catch (IOException e) {
            log.error("Failed to create temp directory for chunked uploads", e);
            throw new RuntimeException("Failed to initialize chunked upload service", e);
        }
    }

    public ChunkedUploadInitResponse initUpload(ChunkedUploadInitRequest request, 
                                                 String ownerId, 
                                                 String ownerUsername) {
        String uploadId = UUID.randomUUID().toString();
        int totalChunks = (int) Math.ceil((double) request.getFileSize() / chunkSize);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(sessionTimeoutHours, ChronoUnit.HOURS);

        ChunkedUploadSession session = ChunkedUploadSession.builder()
                .uploadId(uploadId)
                .filename(request.getFilename())
                .contentType(request.getContentType())
                .fileSize(request.getFileSize())
                .totalChunks(totalChunks)
                .chunkSize(chunkSize)
                .ownerId(ownerId)
                .ownerUsername(ownerUsername)
                .tags(request.getTags())
                .description(request.getDescription())
                .createdAt(now)
                .expiresAt(expiresAt)
                .build();

        sessions.put(uploadId, session);
        
        try {
            Files.createDirectories(tempDir.resolve(uploadId));
        } catch (IOException e) {
            sessions.remove(uploadId);
            throw new StorageException("Failed to create upload directory", e);
        }

        log.info("Initialized chunked upload: uploadId={}, filename={}, totalChunks={}", 
                uploadId, request.getFilename(), totalChunks);

        return ChunkedUploadInitResponse.builder()
                .uploadId(uploadId)
                .totalChunks(totalChunks)
                .chunkSize(chunkSize)
                .expiresAt(expiresAt)
                .build();
    }

    public ChunkUploadResponse uploadChunk(String uploadId, int chunkIndex, MultipartFile chunk) {
        ChunkedUploadSession session = sessions.get(uploadId);
        if (session == null) {
            throw new StorageException("Upload session not found: " + uploadId);
        }
        
        if (session.isExpired()) {
            cleanupSession(uploadId);
            throw new StorageException("Upload session expired: " + uploadId);
        }

        if (chunkIndex < 0 || chunkIndex >= session.getTotalChunks()) {
            throw new StorageException("Invalid chunk index: " + chunkIndex);
        }

        Path chunkPath = tempDir.resolve(uploadId).resolve("chunk-" + chunkIndex);
        try {
            chunk.transferTo(chunkPath);
            session.markChunkComplete(chunkIndex);
            
            log.debug("Uploaded chunk: uploadId={}, chunkIndex={}, progress={}%", 
                    uploadId, chunkIndex, String.format("%.1f", session.getProgressPercent()));

            return ChunkUploadResponse.builder()
                    .chunkIndex(chunkIndex)
                    .completedChunks(session.getCompletedChunks())
                    .totalChunks(session.getTotalChunks())
                    .uploadedChunks(session.getUploadedChunks())
                    .progressPercent(session.getProgressPercent())
                    .build();
        } catch (IOException e) {
            throw new StorageException("Failed to save chunk", e);
        }
    }

    public ChunkedUploadStatusResponse getStatus(String uploadId) {
        ChunkedUploadSession session = sessions.get(uploadId);
        if (session == null) {
            throw new StorageException("Upload session not found: " + uploadId);
        }

        return ChunkedUploadStatusResponse.builder()
                .uploadId(uploadId)
                .filename(session.getFilename())
                .completedChunks(session.getCompletedChunks())
                .totalChunks(session.getTotalChunks())
                .uploadedChunks(session.getUploadedChunks())
                .missingChunks(session.getMissingChunks())
                .progressPercent(session.getProgressPercent())
                .status(session.getStatus())
                .createdAt(session.getCreatedAt())
                .expiresAt(session.getExpiresAt())
                .build();
    }

    public DocumentDTO completeUpload(String uploadId, ChunkedUploadCompleteRequest request) {
        ChunkedUploadSession session = sessions.get(uploadId);
        if (session == null) {
            throw new StorageException("Upload session not found: " + uploadId);
        }

        if (session.isExpired()) {
            cleanupSession(uploadId);
            throw new StorageException("Upload session expired: " + uploadId);
        }

        if (!session.isComplete()) {
            throw new StorageException("Upload not complete. Missing chunks: " + session.getMissingChunks());
        }

        try {
            String storageKey = assembleAndUpload(session);

            Document document = Document.builder()
                    .filename(session.getFilename())
                    .originalFilename(session.getFilename())
                    .contentType(session.getContentType())
                    .size(session.getFileSize())
                    .storageKey(storageKey)
                    .ownerId(session.getOwnerId())
                    .ownerUsername(session.getOwnerUsername())
                    .tags(request != null && request.getTags() != null ? request.getTags() : session.getTags())
                    .description(request != null && request.getDescription() != null ? request.getDescription() : session.getDescription())
                    .build();

            Document savedDocument = documentRepository.save(document);
            
            cleanupSession(uploadId);
            
            log.info("Completed chunked upload: uploadId={}, documentId={}", uploadId, savedDocument.getId());

            return toDTO(savedDocument);
        } catch (Exception e) {
            log.error("Failed to complete chunked upload: uploadId={}", uploadId, e);
            throw new StorageException("Failed to complete upload", e);
        }
    }

    public void cancelUpload(String uploadId) {
        ChunkedUploadSession session = sessions.get(uploadId);
        if (session == null) {
            throw new StorageException("Upload session not found: " + uploadId);
        }
        
        cleanupSession(uploadId);
        log.info("Cancelled chunked upload: uploadId={}", uploadId);
    }

    private String assembleAndUpload(ChunkedUploadSession session) throws IOException {
        Path uploadDir = tempDir.resolve(session.getUploadId());
        Path assembledFile = uploadDir.resolve("assembled");

        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(assembledFile))) {
            for (int i = 0; i < session.getTotalChunks(); i++) {
                Path chunkPath = uploadDir.resolve("chunk-" + i);
                Files.copy(chunkPath, out);
            }
        }

        try (InputStream in = new BufferedInputStream(Files.newInputStream(assembledFile))) {
            return storageService.uploadFile(
                    in,
                    session.getContentType(),
                    session.getFileSize(),
                    session.getFilename()
            );
        }
    }

    private void cleanupSession(String uploadId) {
        sessions.remove(uploadId);
        Path uploadDir = tempDir.resolve(uploadId);
        
        try {
            if (Files.exists(uploadDir)) {
                Files.walk(uploadDir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                log.warn("Failed to delete: {}", path, e);
                            }
                        });
            }
        } catch (IOException e) {
            log.warn("Failed to cleanup upload directory: {}", uploadDir, e);
        }
    }

    @Scheduled(fixedRate = 3600000)
    public void cleanupExpiredSessions() {
        log.debug("Running expired session cleanup");
        sessions.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired()) {
                cleanupSession(entry.getKey());
                log.info("Cleaned up expired session: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    private DocumentDTO toDTO(Document document) {
        return DocumentDTO.builder()
                .id(document.getId())
                .filename(document.getFilename())
                .originalFilename(document.getOriginalFilename())
                .contentType(document.getContentType())
                .size(document.getSize())
                .ownerId(document.getOwnerId())
                .ownerUsername(document.getOwnerUsername())
                .tags(document.getTags())
                .description(document.getDescription())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }
}
