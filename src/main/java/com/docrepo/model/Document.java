package com.docrepo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@org.springframework.data.mongodb.core.mapping.Document(collection = "documents")
@CompoundIndexes({
    @CompoundIndex(name = "owner_created_idx", def = "{'ownerId': 1, 'createdAt': -1}"),
    @CompoundIndex(name = "tags_created_idx", def = "{'tags': 1, 'createdAt': -1}")
})
public class Document {

    @Id
    private String id;

    private String filename;

    private String originalFilename;

    private String contentType;

    private Long size;

    @Field("storageKey")
    private String storageKey;

    @Indexed
    private String ownerId;

    private String ownerUsername;

    @Indexed
    private List<String> tags;

    private String description;

    @CreatedDate
    @Indexed
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
