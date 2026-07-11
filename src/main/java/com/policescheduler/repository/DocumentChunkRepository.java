package com.policescheduler.repository;

import com.policescheduler.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    @Query(value = "SELECT * FROM document_chunks dc " +
            "WHERE 1 - (dc.embedding <=> CAST(:embedding AS vector)) >= :threshold " +
            "ORDER BY dc.embedding <=> CAST(:embedding AS vector) " +
            "LIMIT :limit",
            nativeQuery = true)
    List<DocumentChunk> findSimilarChunks(
            @Param("embedding") String embedding,
            @Param("threshold") double threshold,
            @Param("limit") int limit);
}
