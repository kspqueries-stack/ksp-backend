package com.policescheduler.chat.executor;

import com.policescheduler.chat.model.ProcessingContext;
import com.policescheduler.chat.rag.EmbeddingService;
import com.policescheduler.entity.DocumentChunk;
import com.policescheduler.repository.DocumentChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DocumentMcpServer implements McpServer {

    private static final Logger log = LoggerFactory.getLogger(DocumentMcpServer.class);

    private final DocumentChunkRepository documentChunkRepository;
    private final EmbeddingService embeddingService;

    public DocumentMcpServer(DocumentChunkRepository documentChunkRepository,
                             EmbeddingService embeddingService) {
        this.documentChunkRepository = documentChunkRepository;
        this.embeddingService = embeddingService;
    }

    @Override
    public String getServerId() {
        return "document";
    }

    @Override
    public List<McpToolDefinition> listTools() {
        return List.of(
            new McpToolDefinition("search_documents",
                "Search uploaded documents by text similarity",
                Map.of("query", "string", "top_k", "integer (default 5)"))
        );
    }

    @Override
    public McpToolResult executeTool(String toolName, Map<String, Object> parameters, ProcessingContext context) {
        if ("search_documents".equals(toolName)) {
            return searchDocuments(parameters);
        }
        return McpToolResult.failure("UNKNOWN_TOOL", "Unknown tool: " + toolName);
    }

    private McpToolResult searchDocuments(Map<String, Object> parameters) {
        String query = (String) parameters.getOrDefault("query", "");
        if (query.isBlank()) {
            return McpToolResult.failure("INVALID_PARAMS", "Query parameter is required");
        }

        int topK = 5;
        Object topKParam = parameters.get("top_k");
        if (topKParam instanceof Number) {
            topK = ((Number) topKParam).intValue();
        } else if (topKParam instanceof String) {
            try { topK = Integer.parseInt((String) topKParam); } catch (NumberFormatException ignored) {}
        }

        try {
            float[] queryEmbedding = embeddingService.generateEmbedding(query);
            if (queryEmbedding.length == 0) {
                return McpToolResult.failure("EMBEDDING_ERROR", "Failed to generate embedding for query");
            }

            String embeddingStr = Arrays.toString(queryEmbedding);
            List<DocumentChunk> chunks = documentChunkRepository.findSimilarChunks(embeddingStr, 0.3, topK);

            if (chunks.isEmpty()) {
                return McpToolResult.success(Map.of(
                        "message", "No matching documents found for: " + query,
                        "results", List.of()));
            }

            List<Map<String, Object>> results = chunks.stream()
                    .map(c -> Map.<String, Object>of(
                            "chunkId", c.getId(),
                            "pdfUploadId", c.getPdfUploadId(),
                            "chunkIndex", c.getChunkIndex(),
                            "text", c.getChunkText()))
                    .collect(Collectors.toList());

            return McpToolResult.success(Map.of(
                    "message", "Found " + results.size() + " matching document chunks",
                    "results", results));
        } catch (Exception e) {
            log.error("Document search failed for query: {}", query, e);
            return McpToolResult.failure("SEARCH_ERROR", "Document search failed: " + e.getMessage());
        }
    }
}
