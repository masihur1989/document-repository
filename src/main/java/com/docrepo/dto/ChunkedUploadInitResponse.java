package com.docrepo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkedUploadInitResponse {
    
    private String uploadId;
    private int totalChunks;
    private long chunkSize;
    private Instant expiresAt;
}
