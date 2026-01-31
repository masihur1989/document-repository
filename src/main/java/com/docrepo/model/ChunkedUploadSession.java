package com.docrepo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkedUploadSession {
    
    private String uploadId;
    private String filename;
    private String contentType;
    private long fileSize;
    private int totalChunks;
    private long chunkSize;
    private String ownerId;
    private String ownerUsername;
    private List<String> tags;
    private String description;
    private Instant createdAt;
    private Instant expiresAt;
    
    @Builder.Default
    private Set<Integer> uploadedChunks = new HashSet<>();
    
    public void markChunkComplete(int chunkIndex) {
        uploadedChunks.add(chunkIndex);
    }
    
    public boolean isChunkUploaded(int chunkIndex) {
        return uploadedChunks.contains(chunkIndex);
    }
    
    public int getCompletedChunks() {
        return uploadedChunks.size();
    }
    
    public boolean isComplete() {
        return uploadedChunks.size() == totalChunks;
    }
    
    public Set<Integer> getMissingChunks() {
        return IntStream.range(0, totalChunks)
                .filter(i -> !uploadedChunks.contains(i))
                .boxed()
                .collect(Collectors.toSet());
    }
    
    public double getProgressPercent() {
        if (totalChunks == 0) return 0;
        return (double) uploadedChunks.size() / totalChunks * 100;
    }
    
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
    
    public String getStatus() {
        if (isExpired()) return "EXPIRED";
        if (isComplete()) return "COMPLETE";
        return "IN_PROGRESS";
    }
}
