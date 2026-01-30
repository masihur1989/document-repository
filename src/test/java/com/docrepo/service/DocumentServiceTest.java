package com.docrepo.service;

import com.docrepo.dto.DocumentDTO;
import com.docrepo.dto.DocumentUpdateRequest;
import com.docrepo.exception.DocumentNotFoundException;
import com.docrepo.model.Document;
import com.docrepo.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private DocumentService documentService;

    private Document testDocument;
    private final String documentId = "doc123";
    private final String ownerId = "user123";
    private final String ownerUsername = "testuser";

    @BeforeEach
    void setUp() {
        testDocument = new Document();
        testDocument.setId(documentId);
        testDocument.setFilename("test.pdf");
        testDocument.setOriginalFilename("test.pdf");
        testDocument.setContentType("application/pdf");
        testDocument.setSize(1024L);
        testDocument.setStorageKey("storage-key-123");
        testDocument.setOwnerId(ownerId);
        testDocument.setOwnerUsername(ownerUsername);
        testDocument.setTags(List.of("tag1", "tag2"));
        testDocument.setDescription("Test document");
        testDocument.setCreatedAt(Instant.now());
        testDocument.setUpdatedAt(Instant.now());
    }

    @Test
    void uploadDocument_Success() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "test content".getBytes()
        );
        String storageKey = "storage-key-123";

        when(storageService.uploadFile(any(InputStream.class), eq("application/pdf"), eq(12L), eq("test.pdf")))
                .thenReturn(storageKey);
        when(documentRepository.save(any(Document.class))).thenReturn(testDocument);

        DocumentDTO result = documentService.uploadDocument(
                file, List.of("tag1"), "description", ownerId, ownerUsername
        );

        assertNotNull(result);
        assertEquals("test.pdf", result.getFilename());
        verify(storageService).uploadFile(any(InputStream.class), eq("application/pdf"), eq(12L), eq("test.pdf"));
        verify(documentRepository).save(any(Document.class));
    }

    @Test
    void getDocument_Success() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(testDocument));

        DocumentDTO result = documentService.getDocument(documentId);

        assertNotNull(result);
        assertEquals(documentId, result.getId());
        assertEquals("test.pdf", result.getFilename());
        verify(documentRepository).findById(documentId);
    }

    @Test
    void getDocument_NotFound() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        assertThrows(DocumentNotFoundException.class, () -> documentService.getDocument(documentId));
        verify(documentRepository).findById(documentId);
    }

    @Test
    void getDocumentEntity_Success() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(testDocument));

        Document result = documentService.getDocumentEntity(documentId);

        assertNotNull(result);
        assertEquals(documentId, result.getId());
        verify(documentRepository).findById(documentId);
    }

    @Test
    void listDocuments_Success() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Document> documentPage = new PageImpl<>(List.of(testDocument));
        when(documentRepository.findAll(pageable)).thenReturn(documentPage);

        Page<DocumentDTO> result = documentService.listDocuments(pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(documentRepository).findAll(pageable);
    }

    @Test
    void listDocumentsByOwner_Success() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Document> documentPage = new PageImpl<>(List.of(testDocument));
        when(documentRepository.findByOwnerId(ownerId, pageable)).thenReturn(documentPage);

        Page<DocumentDTO> result = documentService.listDocumentsByOwner(ownerId, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(documentRepository).findByOwnerId(ownerId, pageable);
    }

    @Test
    void listDocumentsByTag_Success() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Document> documentPage = new PageImpl<>(List.of(testDocument));
        when(documentRepository.findByTagsContaining("tag1", pageable)).thenReturn(documentPage);

        Page<DocumentDTO> result = documentService.listDocumentsByTag("tag1", pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(documentRepository).findByTagsContaining("tag1", pageable);
    }

    @Test
    void searchDocuments_Success() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Document> documentPage = new PageImpl<>(List.of(testDocument));
        when(documentRepository.searchByFilenameOrDescription("test", pageable)).thenReturn(documentPage);

        Page<DocumentDTO> result = documentService.searchDocuments("test", pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(documentRepository).searchByFilenameOrDescription("test", pageable);
    }

    @Test
    void updateDocument_Success() {
        DocumentUpdateRequest updateRequest = new DocumentUpdateRequest();
        updateRequest.setFilename("updated.pdf");
        updateRequest.setTags(List.of("newtag"));
        updateRequest.setDescription("Updated description");

        Document updatedDocument = new Document();
        updatedDocument.setId(documentId);
        updatedDocument.setFilename("updated.pdf");
        updatedDocument.setOriginalFilename("test.pdf");
        updatedDocument.setContentType("application/pdf");
        updatedDocument.setSize(1024L);
        updatedDocument.setStorageKey("storage-key-123");
        updatedDocument.setOwnerId(ownerId);
        updatedDocument.setOwnerUsername(ownerUsername);
        updatedDocument.setTags(List.of("newtag"));
        updatedDocument.setDescription("Updated description");

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(testDocument));
        when(documentRepository.save(any(Document.class))).thenReturn(updatedDocument);

        DocumentDTO result = documentService.updateDocument(documentId, updateRequest);

        assertNotNull(result);
        assertEquals("updated.pdf", result.getFilename());
        assertEquals("Updated description", result.getDescription());
        verify(documentRepository).findById(documentId);
        verify(documentRepository).save(any(Document.class));
    }

    @Test
    void updateDocument_NotFound() {
        DocumentUpdateRequest updateRequest = new DocumentUpdateRequest();
        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        assertThrows(DocumentNotFoundException.class, 
                () -> documentService.updateDocument(documentId, updateRequest));
    }

    @Test
    void deleteDocument_Success() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(testDocument));
        doNothing().when(storageService).deleteFile(testDocument.getStorageKey());
        doNothing().when(documentRepository).delete(testDocument);

        documentService.deleteDocument(documentId);

        verify(documentRepository).findById(documentId);
        verify(storageService).deleteFile(testDocument.getStorageKey());
        verify(documentRepository).delete(testDocument);
    }

    @Test
    void deleteDocument_NotFound() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        assertThrows(DocumentNotFoundException.class, () -> documentService.deleteDocument(documentId));
    }

    @Test
    void downloadDocument_Success() {
        InputStream mockInputStream = new ByteArrayInputStream("test content".getBytes());
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(testDocument));
        when(storageService.downloadFile(testDocument.getStorageKey())).thenReturn(mockInputStream);

        InputStream result = documentService.downloadDocument(documentId);

        assertNotNull(result);
        verify(documentRepository).findById(documentId);
        verify(storageService).downloadFile(testDocument.getStorageKey());
    }

    @Test
    void isOwner_ReturnsTrue() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(testDocument));

        boolean result = documentService.isOwner(documentId, ownerId);

        assertTrue(result);
    }

    @Test
    void isOwner_ReturnsFalse_DifferentOwner() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(testDocument));

        boolean result = documentService.isOwner(documentId, "differentUser");

        assertFalse(result);
    }

    @Test
    void isOwner_ReturnsFalse_DocumentNotFound() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        boolean result = documentService.isOwner(documentId, ownerId);

        assertFalse(result);
    }
}
