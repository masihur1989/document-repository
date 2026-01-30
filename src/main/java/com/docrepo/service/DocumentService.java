package com.docrepo.service;

import com.docrepo.dto.DocumentDTO;
import com.docrepo.dto.DocumentUpdateRequest;
import com.docrepo.exception.DocumentNotFoundException;
import com.docrepo.model.Document;
import com.docrepo.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final StorageService storageService;

    public DocumentDTO uploadDocument(MultipartFile file, List<String> tags, String description,
                                       String ownerId, String ownerUsername) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String contentType = file.getContentType();
        long size = file.getSize();

        String storageKey = storageService.uploadFile(
                file.getInputStream(),
                contentType,
                size,
                originalFilename
        );

        Document document = Document.builder()
                .filename(originalFilename)
                .originalFilename(originalFilename)
                .contentType(contentType)
                .size(size)
                .storageKey(storageKey)
                .ownerId(ownerId)
                .ownerUsername(ownerUsername)
                .tags(tags)
                .description(description)
                .build();

        Document savedDocument = documentRepository.save(document);
        log.info("Document uploaded successfully: id={}, filename={}", savedDocument.getId(), originalFilename);

        return toDTO(savedDocument);
    }

    @Cacheable(value = "documents", key = "#id")
    public DocumentDTO getDocument(String id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
        return toDTO(document);
    }

    public Document getDocumentEntity(String id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    public Page<DocumentDTO> listDocuments(Pageable pageable) {
        return documentRepository.findAll(pageable).map(this::toDTO);
    }

    public Page<DocumentDTO> listDocumentsByOwner(String ownerId, Pageable pageable) {
        return documentRepository.findByOwnerId(ownerId, pageable).map(this::toDTO);
    }

    public Page<DocumentDTO> listDocumentsByTag(String tag, Pageable pageable) {
        return documentRepository.findByTagsContaining(tag, pageable).map(this::toDTO);
    }

    public Page<DocumentDTO> searchDocuments(String searchTerm, Pageable pageable) {
        return documentRepository.searchByFilenameOrDescription(searchTerm, pageable).map(this::toDTO);
    }

    @CacheEvict(value = "documents", key = "#id")
    public DocumentDTO updateDocument(String id, DocumentUpdateRequest updateRequest) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));

        if (updateRequest.getFilename() != null) {
            document.setFilename(updateRequest.getFilename());
        }
        if (updateRequest.getTags() != null) {
            document.setTags(updateRequest.getTags());
        }
        if (updateRequest.getDescription() != null) {
            document.setDescription(updateRequest.getDescription());
        }

        Document updatedDocument = documentRepository.save(document);
        log.info("Document updated: id={}", id);

        return toDTO(updatedDocument);
    }

    @CacheEvict(value = "documents", key = "#id")
    public void deleteDocument(String id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));

        storageService.deleteFile(document.getStorageKey());
        documentRepository.delete(document);
        log.info("Document deleted: id={}", id);
    }

    public InputStream downloadDocument(String id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
        return storageService.downloadFile(document.getStorageKey());
    }

    public boolean isOwner(String documentId, String userId) {
        return documentRepository.findById(documentId)
                .map(doc -> doc.getOwnerId().equals(userId))
                .orElse(false);
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
