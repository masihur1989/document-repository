package com.docrepo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkedUploadInitRequest {
    
    @NotBlank(message = "Filename is required")
    private String filename;
    
    @NotBlank(message = "Content type is required")
    private String contentType;
    
    @NotNull(message = "File size is required")
    @Positive(message = "File size must be positive")
    private Long fileSize;
    
    private List<String> tags;
    
    private String description;
}
