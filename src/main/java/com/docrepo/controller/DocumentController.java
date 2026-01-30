package com.docrepo.controller;

import com.docrepo.dto.DocumentDTO;
import com.docrepo.dto.DocumentUpdateRequest;
import com.docrepo.model.Document;
import com.docrepo.security.UserPrincipal;
import com.docrepo.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Documents", description = "Document management API for upload, download, and metadata operations")
@SecurityRequirement(name = "bearerAuth")
public class DocumentController {

    private final DocumentService documentService;

    @Operation(summary = "Upload a new document", 
            description = "Upload a document file with optional tags and description. Streams directly to MinIO for performance.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Document uploaded successfully",
                    content = @Content(schema = @Schema(implementation = DocumentDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid file or empty file"),
            @ApiResponse(responseCode = "403", description = "Access denied - requires ADMIN or EDITOR role")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<DocumentDTO> uploadDocument(
            @Parameter(description = "File to upload", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "List of tags for the document")
            @RequestParam(value = "tags", required = false) List<String> tags,
            @Parameter(description = "Description of the document")
            @RequestParam(value = "description", required = false) String description,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal) throws IOException {

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

    @Operation(summary = "List all documents", 
            description = "Get paginated list of all documents. Default: 20 items per page, sorted by createdAt descending.")
    @ApiResponse(responseCode = "200", description = "Documents retrieved successfully")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<DocumentDTO>> listDocuments(
            @Parameter(description = "Pagination parameters") 
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<DocumentDTO> documents = documentService.listDocuments(pageable);
        return ResponseEntity.ok(documents);
    }

    @Operation(summary = "List documents by owner", description = "Get paginated list of documents owned by a specific user")
    @ApiResponse(responseCode = "200", description = "Documents retrieved successfully")
    @GetMapping("/owner/{ownerId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<DocumentDTO>> listDocumentsByOwner(
            @Parameter(description = "Owner user ID") @PathVariable String ownerId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<DocumentDTO> documents = documentService.listDocumentsByOwner(ownerId, pageable);
        return ResponseEntity.ok(documents);
    }

    @Operation(summary = "List documents by tag", description = "Get paginated list of documents with a specific tag")
    @ApiResponse(responseCode = "200", description = "Documents retrieved successfully")
    @GetMapping("/tag/{tag}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<DocumentDTO>> listDocumentsByTag(
            @Parameter(description = "Tag to filter by") @PathVariable String tag,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<DocumentDTO> documents = documentService.listDocumentsByTag(tag, pageable);
        return ResponseEntity.ok(documents);
    }

    @Operation(summary = "Search documents", description = "Search documents by filename or description")
    @ApiResponse(responseCode = "200", description = "Search results retrieved successfully")
    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<DocumentDTO>> searchDocuments(
            @Parameter(description = "Search term to match against filename or description") @RequestParam("q") String searchTerm,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<DocumentDTO> documents = documentService.searchDocuments(searchTerm, pageable);
        return ResponseEntity.ok(documents);
    }

    @Operation(summary = "Get document metadata", description = "Get document metadata by ID. Response is cached for performance.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Document metadata retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DocumentDTO> getDocument(
            @Parameter(description = "Document ID") @PathVariable String id) {
        DocumentDTO document = documentService.getDocument(id);
        return ResponseEntity.ok(document);
    }

    @Operation(summary = "Download document file", 
            description = "Download the document file. Streams directly from MinIO for performance (zero-copy).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "File downloaded successfully"),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    @GetMapping("/{id}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> downloadDocument(
            @Parameter(description = "Document ID") @PathVariable String id) {
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

    @Operation(summary = "Update document metadata", 
            description = "Update document metadata. ADMIN can update any document, EDITOR can only update own documents.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Document updated successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @documentSecurity.isOwner(#id, authentication)")
    public ResponseEntity<DocumentDTO> updateDocument(
            @Parameter(description = "Document ID") @PathVariable String id,
            @Valid @RequestBody DocumentUpdateRequest updateRequest) {

        log.info("Update request: documentId={}", id);
        DocumentDTO document = documentService.updateDocument(id, updateRequest);
        return ResponseEntity.ok(document);
    }

    @Operation(summary = "Delete a document", 
            description = "Delete a document from both MinIO (file) and MongoDB (metadata). ADMIN can delete any document, EDITOR can only delete own documents.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Document deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @documentSecurity.isOwner(#id, authentication)")
    public ResponseEntity<Void> deleteDocument(
            @Parameter(description = "Document ID") @PathVariable String id) {
        log.info("Delete request: documentId={}", id);
        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get my documents", description = "Get paginated list of documents owned by the current authenticated user")
    @ApiResponse(responseCode = "200", description = "Documents retrieved successfully")
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<DocumentDTO>> getMyDocuments(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<DocumentDTO> documents = documentService.listDocumentsByOwner(userPrincipal.getId(), pageable);
        return ResponseEntity.ok(documents);
    }
}
