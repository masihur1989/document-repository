package com.docrepo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkUploadResponse {
    
    private int chunkIndex;
    private int completedChunks;
    private int totalChunks;
    private Set<Integer> uploadedChunks;
    private double progressPercent;
}
