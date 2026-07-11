package com.policescheduler.chat.rag;

import com.policescheduler.entity.DocumentChunk;
import com.policescheduler.repository.DocumentChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagRetrieverService {

    private static final Logger log = LoggerFactory.getLogger(RagRetrieverService.class);

    private final DocumentChunkRepository documentChunkRepository;
    private final EmbeddingService embeddingService;

    public RagRetrieverService(DocumentChunkRepository documentChunkRepository,
                               EmbeddingService embeddingService) {
        this.documentChunkRepository = documentChunkRepository;
        this.embeddingService = embeddingService;
    }

    public List<String> retrieve(String query, double threshold, int topK) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        try {
            float[] queryEmbedding = embeddingService.generateEmbedding(query);
            if (queryEmbedding.length == 0) {
                log.warn("Empty embedding generated for query, returning no results");
                return Collections.emptyList();
            }

            String embeddingStr = Arrays.toString(queryEmbedding);
            List<DocumentChunk> chunks = documentChunkRepository.findSimilarChunks(
                    embeddingStr, threshold, topK);

            if (chunks.isEmpty()) {
                log.debug("No chunks found exceeding threshold {} for query: {}", threshold, query);
                return Collections.emptyList();
            }

            return chunks.stream()
                    .map(DocumentChunk::getChunkText)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("RAG retrieval failed for query: {}", query, e);
            return Collections.emptyList();
        }
    }
}
