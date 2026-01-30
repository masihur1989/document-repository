package com.docrepo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDTO {

    private String id;
    private String filename;
    private String originalFilename;
    private String contentType;
    private Long size;
    private String ownerId;
    private String ownerUsername;
    private List<String> tags;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;
}
