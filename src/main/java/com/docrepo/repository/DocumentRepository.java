package com.docrepo.repository;

import com.docrepo.model.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends MongoRepository<Document, String> {

    Page<Document> findByOwnerId(String ownerId, Pageable pageable);

    Page<Document> findByTagsContaining(String tag, Pageable pageable);

    @Query("{ 'tags': { $all: ?0 } }")
    Page<Document> findByTagsContainingAll(List<String> tags, Pageable pageable);

    @Query("{ '$or': [ " +
           "{ 'filename': { $regex: ?0, $options: 'i' } }, " +
           "{ 'description': { $regex: ?0, $options: 'i' } } " +
           "] }")
    Page<Document> searchByFilenameOrDescription(String searchTerm, Pageable pageable);

    long countByOwnerId(String ownerId);
}
