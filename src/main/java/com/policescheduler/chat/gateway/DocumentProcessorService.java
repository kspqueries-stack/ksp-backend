package com.policescheduler.chat.gateway;

import com.policescheduler.chat.rag.EmbeddingService;
import com.policescheduler.dto.ChatResponse;
import com.policescheduler.entity.DocumentChunk;
import com.policescheduler.entity.PdfUpload;
import com.policescheduler.repository.DocumentChunkRepository;
import com.policescheduler.repository.PdfUploadRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentProcessorService {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessorService.class);

    private static final int CHUNK_SIZE_WORDS = 500;
    private static final int OVERLAP_WORDS = 50;

    private final EmbeddingService embeddingService;
    private final DocumentChunkRepository documentChunkRepository;
    private final PdfUploadRepository pdfUploadRepository;

    public DocumentProcessorService(EmbeddingService embeddingService,
                                    DocumentChunkRepository documentChunkRepository,
                                    PdfUploadRepository pdfUploadRepository) {
        this.embeddingService = embeddingService;
        this.documentChunkRepository = documentChunkRepository;
        this.pdfUploadRepository = pdfUploadRepository;
    }

    public ChatResponse processUpload(Long userId, MultipartFile pdfFile) {
        try {
            // Save PDF file
            Path uploadPath = Paths.get("uploads/pdf");
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            String fileName = UUID.randomUUID() + "_" + pdfFile.getOriginalFilename();
            Path filePath = uploadPath.resolve(fileName);
            Files.copy(pdfFile.getInputStream(), filePath);

            // Extract text from PDF
            String extractedText = extractText(filePath);
            if (extractedText == null || extractedText.isBlank()) {
                return ChatResponse.builder()
                        .response("Could not extract text from the uploaded PDF.")
                        .responseType("text")
                        .commandType("UPLOAD_DOCUMENT")
                        .build();
            }

            // Save PdfUpload entity
            PdfUpload pdfUpload = new PdfUpload();
            pdfUpload.setUserId(userId);
            pdfUpload.setFileName(pdfFile.getOriginalFilename());
            pdfUpload.setFilePath(filePath.toString());
            pdfUpload.setProcessingStatus("PROCESSING");
            pdfUpload.setExtractedSummary(extractedText.substring(0, Math.min(500, extractedText.length())));
            pdfUpload = pdfUploadRepository.save(pdfUpload);

            // Chunk text
            List<String> chunks = chunkText(extractedText);

            // Generate embeddings and save chunks
            for (int i = 0; i < chunks.size(); i++) {
                String chunkText = chunks.get(i);
                float[] embedding = embeddingService.generateEmbedding(chunkText);

                DocumentChunk chunk = DocumentChunk.builder()
                        .pdfUploadId(pdfUpload.getId())
                        .chunkIndex(i)
                        .chunkText(chunkText)
                        .embedding(embedding.length > 0 ? embedding : null)
                        .build();
                documentChunkRepository.save(chunk);
            }

            // Update status
            pdfUpload.setProcessingStatus("PROCESSED");
            pdfUploadRepository.save(pdfUpload);

            log.info("Document processed: {} chunks created for file {}", chunks.size(), pdfFile.getOriginalFilename());

            return ChatResponse.builder()
                    .response("Document uploaded and processed successfully: " + pdfFile.getOriginalFilename()
                            + " (" + chunks.size() + " chunks indexed)")
                    .responseType("text")
                    .commandType("UPLOAD_DOCUMENT")
                    .build();

        } catch (Exception e) {
            log.error("Document processing failed", e);
            return ChatResponse.builder()
                    .response("Unable to process document: " + e.getMessage())
                    .responseType("text")
                    .commandType("UPLOAD_DOCUMENT")
                    .build();
        }
    }

    private String extractText(Path filePath) throws IOException {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    List<String> chunkText(String text) {
        String[] words = text.split("\\s+");
        List<String> chunks = new ArrayList<>();

        int start = 0;
        while (start < words.length) {
            int end = Math.min(start + CHUNK_SIZE_WORDS, words.length);
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                if (i > start) sb.append(' ');
                sb.append(words[i]);
            }
            chunks.add(sb.toString());

            if (end >= words.length) break;
            start = end - OVERLAP_WORDS;
            if (start < 0) start = 0;
        }
        return chunks;
    }
}
