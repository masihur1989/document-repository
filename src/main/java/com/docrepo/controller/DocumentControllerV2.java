package com.docrepo.controller;

import com.docrepo.dto.*;
import com.docrepo.security.UserPrincipal;
import com.docrepo.service.ChunkedUploadService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/v2/documents")
@RequiredArgsConstructor
@Tag(name = "Documents V2 - Chunked Upload", description = "Chunked upload API for large files with resume capability")
@SecurityRequirement(name = "bearerAuth")
public class DocumentControllerV2 {

    private final ChunkedUploadService chunkedUploadService;

    @Operation(summary = "Initialize chunked upload", 
            description = "Initialize a new chunked upload session. Returns uploadId and chunk configuration.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Upload session created successfully",
                    content = @Content(schema = @Schema(implementation = ChunkedUploadInitResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "403", description = "Access denied - requires ADMIN or EDITOR role")
    })
    @PostMapping("/upload/init")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<ChunkedUploadInitResponse> initUpload(
            @Valid @RequestBody ChunkedUploadInitRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal) {

        log.info("Chunked upload init: filename={}, size={}, user={}", 
                request.getFilename(), request.getFileSize(), userPrincipal.getUsername());

        ChunkedUploadInitResponse response = chunkedUploadService.initUpload(
                request,
                userPrincipal.getId(),
                userPrincipal.getUsername()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Upload a chunk", 
            description = "Upload a single chunk of the file. Chunks can be uploaded in any order and in parallel.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Chunk uploaded successfully",
                    content = @Content(schema = @Schema(implementation = ChunkUploadResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid chunk index or data"),
            @ApiResponse(responseCode = "404", description = "Upload session not found"),
            @ApiResponse(responseCode = "410", description = "Upload session expired")
    })
    @PostMapping(value = "/upload/{uploadId}/chunk/{chunkIndex}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<ChunkUploadResponse> uploadChunk(
            @Parameter(description = "Upload session ID") @PathVariable String uploadId,
            @Parameter(description = "Chunk index (0-based)") @PathVariable int chunkIndex,
            @Parameter(description = "Chunk data") @RequestParam("chunk") MultipartFile chunk) {

        log.debug("Chunk upload: uploadId={}, chunkIndex={}, size={}", 
                uploadId, chunkIndex, chunk.getSize());

        ChunkUploadResponse response = chunkedUploadService.uploadChunk(uploadId, chunkIndex, chunk);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get upload status", 
            description = "Get the current status of a chunked upload session including progress and missing chunks.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status retrieved successfully",
                    content = @Content(schema = @Schema(implementation = ChunkedUploadStatusResponse.class))),
            @ApiResponse(responseCode = "404", description = "Upload session not found")
    })
    @GetMapping("/upload/{uploadId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<ChunkedUploadStatusResponse> getUploadStatus(
            @Parameter(description = "Upload session ID") @PathVariable String uploadId) {

        ChunkedUploadStatusResponse response = chunkedUploadService.getStatus(uploadId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Complete chunked upload", 
            description = "Complete the chunked upload after all chunks have been uploaded. Assembles chunks and creates document record.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Upload completed successfully",
                    content = @Content(schema = @Schema(implementation = DocumentDTO.class))),
            @ApiResponse(responseCode = "400", description = "Upload not complete - missing chunks"),
            @ApiResponse(responseCode = "404", description = "Upload session not found"),
            @ApiResponse(responseCode = "410", description = "Upload session expired")
    })
    @PostMapping("/upload/{uploadId}/complete")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<DocumentDTO> completeUpload(
            @Parameter(description = "Upload session ID") @PathVariable String uploadId,
            @RequestBody(required = false) ChunkedUploadCompleteRequest request) {

        log.info("Completing chunked upload: uploadId={}", uploadId);

        DocumentDTO document = chunkedUploadService.completeUpload(uploadId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(document);
    }

    @Operation(summary = "Cancel chunked upload", 
            description = "Cancel an in-progress chunked upload and cleanup temporary files.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Upload cancelled successfully"),
            @ApiResponse(responseCode = "404", description = "Upload session not found")
    })
    @DeleteMapping("/upload/{uploadId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<Void> cancelUpload(
            @Parameter(description = "Upload session ID") @PathVariable String uploadId) {

        log.info("Cancelling chunked upload: uploadId={}", uploadId);

        chunkedUploadService.cancelUpload(uploadId);
        return ResponseEntity.noContent().build();
    }
}
