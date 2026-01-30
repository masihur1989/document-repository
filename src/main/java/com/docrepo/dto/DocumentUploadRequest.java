package com.docrepo.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadRequest {

    @Size(max = 10, message = "Maximum 10 tags allowed")
    private List<String> tags;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
}
