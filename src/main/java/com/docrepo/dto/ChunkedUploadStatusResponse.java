package com.docrepo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkedUploadStatusResponse {
    
    private String uploadId;
    private String filename;
    private int completedChunks;
    private int totalChunks;
    private Set<Integer> uploadedChunks;
    private Set<Integer> missingChunks;
    private double progressPercent;
    private String status;
    private Instant createdAt;
    private Instant expiresAt;
}
