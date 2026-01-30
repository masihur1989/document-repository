package com.docrepo.controller;

import com.docrepo.dto.DocumentDTO;
import com.docrepo.dto.DocumentUpdateRequest;
import com.docrepo.model.Document;
import com.docrepo.security.UserPrincipal;
import com.docrepo.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /**
     * Upload a new document.
     * Access: ADMIN, EDITOR
     * 
     * Streams file directly to MinIO for performance.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<DocumentDTO> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "tags", required = false) List<String> tags,
            @RequestParam(value = "description", required = false) String description,
            @AuthenticationPrincipal UserPrincipal userPrincipal) throws IOException {

        log.info("Upload request: filename={}, size={}, user={}", 
                file.getOriginalFilename(), file.getSize(), userPrincipal.getUsername());

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        DocumentDTO document = documentService.uploadDocument(
                file,
                tags,
                description,
                userPrincipal.getId(),
                userPrincipal.getUsername()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(document);
    }

    /**
     * List all documents with pagination.
     * Access: All authenticated users
     * 
     * Default: 20 items per page, sorted by createdAt descending
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<DocumentDTO>> listDocuments(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<DocumentDTO> documents = documentService.listDocuments(pageable);
        return ResponseEntity.ok(documents);
    }

    /**
     * List documents by owner.
     * Access: All authenticated users
     */
    @GetMapping("/owner/{ownerId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<DocumentDTO>> listDocumentsByOwner(
            @PathVariable String ownerId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<DocumentDTO> documents = documentService.listDocumentsByOwner(ownerId, pageable);
        return ResponseEntity.ok(documents);
    }

    /**
     * List documents by tag.
     * Access: All authenticated users
     */
    @GetMapping("/tag/{tag}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<DocumentDTO>> listDocumentsByTag(
            @PathVariable String tag,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<DocumentDTO> documents = documentService.listDocumentsByTag(tag, pageable);
        return ResponseEntity.ok(documents);
    }

    /**
     * Search documents by filename or description.
     * Access: All authenticated users
     */
    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<DocumentDTO>> searchDocuments(
            @RequestParam("q") String searchTerm,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<DocumentDTO> documents = documentService.searchDocuments(searchTerm, pageable);
        return ResponseEntity.ok(documents);
    }

    /**
     * Get document metadata by ID.
     * Access: All authenticated users
     * 
     * Response is cached for performance.
     */
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DocumentDTO> getDocument(@PathVariable String id) {
        DocumentDTO document = documentService.getDocument(id);
        return ResponseEntity.ok(document);
    }

    /**
     * Download document file.
     * Access: All authenticated users
     * 
     * Streams file directly from MinIO for performance (zero-copy).
     */
    @GetMapping("/{id}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> downloadDocument(@PathVariable String id) {
        Document document = documentService.getDocumentEntity(id);
        InputStream inputStream = documentService.downloadDocument(id);

        InputStreamResource resource = new InputStreamResource(inputStream);

        String contentDisposition = String.format("attachment; filename=\"%s\"", 
                document.getOriginalFilename() != null ? document.getOriginalFilename() : document.getFilename());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .header(HttpHeaders.CONTENT_TYPE, document.getContentType())
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(document.getSize()))
                .body(resource);
    }

    /**
     * Update document metadata.
     * Access: ADMIN (any document) or EDITOR (own documents only)
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @documentSecurity.isOwner(#id, authentication)")
    public ResponseEntity<DocumentDTO> updateDocument(
            @PathVariable String id,
            @Valid @RequestBody DocumentUpdateRequest updateRequest) {

        log.info("Update request: documentId={}", id);
        DocumentDTO document = documentService.updateDocument(id, updateRequest);
        return ResponseEntity.ok(document);
    }

    /**
     * Delete a document.
     * Access: ADMIN (any document) or EDITOR (own documents only)
     * 
     * Deletes from both MinIO (file) and MongoDB (metadata).
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @documentSecurity.isOwner(#id, authentication)")
    public ResponseEntity<Void> deleteDocument(@PathVariable String id) {
        log.info("Delete request: documentId={}", id);
        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get my documents (current user's documents).
     * Access: All authenticated users
     */
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<DocumentDTO>> getMyDocuments(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<DocumentDTO> documents = documentService.listDocumentsByOwner(userPrincipal.getId(), pageable);
        return ResponseEntity.ok(documents);
    }
}
