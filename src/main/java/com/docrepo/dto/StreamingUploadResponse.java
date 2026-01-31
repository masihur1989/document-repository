package com.docrepo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StreamingUploadResponse {
    
    private DocumentDTO document;
    private long uploadDurationMs;
    private long bytesPerSecond;
    private String uploadMethod;
}
