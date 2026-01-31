package com.docrepo.controller;

import com.docrepo.dto.DocumentDTO;
import com.docrepo.dto.StreamingUploadResponse;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v3/documents")
@RequiredArgsConstructor
@Tag(name = "Documents V3 - Enhanced Streaming", description = "Optimized streaming API for large file uploads and downloads")
@SecurityRequirement(name = "bearerAuth")
public class DocumentControllerV3 {

    private final DocumentService documentService;

    private static final int BUFFER_SIZE = 64 * 1024; // 64KB buffer for streaming

    @Operation(summary = "Upload document with enhanced streaming", 
            description = "Upload a document using optimized streaming. Supports files up to 1GB with progress tracking.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Document uploaded successfully",
                    content = @Content(schema = @Schema(implementation = StreamingUploadResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid file or empty file"),
            @ApiResponse(responseCode = "403", description = "Access denied - requires ADMIN or EDITOR role"),
            @ApiResponse(responseCode = "413", description = "File too large")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public ResponseEntity<StreamingUploadResponse> uploadDocument(
            @Parameter(description = "File to upload (max 1GB)", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "List of tags for the document")
            @RequestParam(value = "tags", required = false) List<String> tags,
            @Parameter(description = "Description of the document")
            @RequestParam(value = "description", required = false) String description,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal userPrincipal) throws IOException {

        long startTime = System.currentTimeMillis();
        
        log.info("V3 Streaming upload: filename={}, size={}, user={}", 
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

        long duration = System.currentTimeMillis() - startTime;
        long bytesPerSecond = duration > 0 ? (file.getSize() * 1000) / duration : 0;

        StreamingUploadResponse response = StreamingUploadResponse.builder()
                .document(document)
                .uploadDurationMs(duration)
                .bytesPerSecond(bytesPerSecond)
                .uploadMethod("STREAMING_V3")
                .build();

        log.info("V3 Upload complete: documentId={}, duration={}ms, speed={} bytes/s", 
                document.getId(), duration, bytesPerSecond);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Download document with streaming response", 
            description = "Download document using StreamingResponseBody for optimal memory usage with large files.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "File download started"),
            @ApiResponse(responseCode = "404", description = "Document not found")
    })
    @GetMapping("/{id}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StreamingResponseBody> downloadDocumentStreaming(
            @Parameter(description = "Document ID") @PathVariable String id) {
        
        Document document = documentService.getDocumentEntity(id);
        
        log.info("V3 Streaming download: documentId={}, filename={}, size={}", 
                id, document.getOriginalFilename(), document.getSize());

        StreamingResponseBody responseBody = outputStream -> {
            try (InputStream inputStream = documentService.downloadDocument(id)) {
                streamWithBuffer(inputStream, outputStream);
            }
        };

        String contentDisposition = String.format("attachment; filename=\"%s\"", 
                document.getOriginalFilename() != null ? document.getOriginalFilename() : document.getFilename());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .header(HttpHeaders.CONTENT_TYPE, document.getContentType())
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(document.getSize()))
                .header("X-Download-Method", "STREAMING_V3")
                .body(responseBody);
    }

    @Operation(summary = "Download document with range support", 
            description = "Download document with HTTP Range header support for partial downloads and resume capability.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Full file download"),
            @ApiResponse(responseCode = "206", description = "Partial content (range request)"),
            @ApiResponse(responseCode = "404", description = "Document not found"),
            @ApiResponse(responseCode = "416", description = "Range not satisfiable")
    })
    @GetMapping("/{id}/download/range")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> downloadDocumentWithRange(
            @Parameter(description = "Document ID") @PathVariable String id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        
        Document document = documentService.getDocumentEntity(id);
        long fileSize = document.getSize();
        
        log.info("V3 Range download: documentId={}, range={}", id, rangeHeader);

        if (rangeHeader == null) {
            InputStream inputStream = documentService.downloadDocument(id);
            InputStreamResource resource = new InputStreamResource(inputStream);

            String contentDisposition = String.format("attachment; filename=\"%s\"", 
                    document.getOriginalFilename() != null ? document.getOriginalFilename() : document.getFilename());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                    .header(HttpHeaders.CONTENT_TYPE, document.getContentType())
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileSize))
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .body(resource);
        }

        long[] range = parseRangeHeader(rangeHeader, fileSize);
        if (range == null) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize)
                    .build();
        }

        long start = range[0];
        long end = range[1];
        long contentLength = end - start + 1;

        InputStream inputStream = documentService.downloadDocument(id);
        try {
            inputStream.skip(start);
        } catch (IOException e) {
            log.error("Failed to skip to range start", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        InputStreamResource resource = new InputStreamResource(
                new BoundedInputStream(inputStream, contentLength));

        String contentDisposition = String.format("attachment; filename=\"%s\"", 
                document.getOriginalFilename() != null ? document.getOriginalFilename() : document.getFilename());

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .header(HttpHeaders.CONTENT_TYPE, document.getContentType())
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(contentLength))
                .header(HttpHeaders.CONTENT_RANGE, String.format("bytes %d-%d/%d", start, end, fileSize))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(resource);
    }

    private void streamWithBuffer(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
            output.flush();
        }
    }

    private long[] parseRangeHeader(String rangeHeader, long fileSize) {
        if (rangeHeader == null || !rangeHeader.startsWith("bytes=")) {
            return null;
        }

        String range = rangeHeader.substring(6);
        String[] parts = range.split("-");

        try {
            long start;
            long end;

            if (parts[0].isEmpty()) {
                long suffixLength = Long.parseLong(parts[1]);
                start = fileSize - suffixLength;
                end = fileSize - 1;
            } else if (parts.length == 1 || parts[1].isEmpty()) {
                start = Long.parseLong(parts[0]);
                end = fileSize - 1;
            } else {
                start = Long.parseLong(parts[0]);
                end = Long.parseLong(parts[1]);
            }

            if (start < 0 || start >= fileSize || end < start || end >= fileSize) {
                return null;
            }

            return new long[]{start, end};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static class BoundedInputStream extends InputStream {
        private final InputStream in;
        private long remaining;

        public BoundedInputStream(InputStream in, long limit) {
            this.in = in;
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int result = in.read();
            if (result != -1) {
                remaining--;
            }
            return result;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int toRead = (int) Math.min(len, remaining);
            int result = in.read(b, off, toRead);
            if (result != -1) {
                remaining -= result;
            }
            return result;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }
}
