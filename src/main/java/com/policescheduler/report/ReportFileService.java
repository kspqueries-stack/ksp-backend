package com.policescheduler.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.UUID;

@Service
public class ReportFileService {

    private static final Logger log = LoggerFactory.getLogger(ReportFileService.class);
    private static final String REPORT_DIR = "ksp-reports";
    private static final long ONE_HOUR_MILLIS = 3600_000L;

    private final Path storageDir;

    public ReportFileService() {
        this.storageDir = Paths.get(System.getProperty("java.io.tmpdir"), REPORT_DIR);
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            log.error("Failed to create report storage directory: {}", storageDir, e);
        }
    }

    /**
     * Stores PDF bytes to the temp directory with a UUID filename.
     * @return the unique file ID
     */
    public String store(byte[] pdfBytes, String suggestedFilename) {
        String fileId = UUID.randomUUID().toString();
        Path filePath = storageDir.resolve(fileId + ".pdf");
        try {
            Files.write(filePath, pdfBytes);
            log.info("Stored report file: {} (suggested name: {})", fileId, suggestedFilename);
        } catch (IOException e) {
            log.error("Failed to store report file: {}", fileId, e);
            throw new RuntimeException("Failed to store report file", e);
        }
        return fileId;
    }

    /**
     * Retrieves PDF bytes by file ID.
     * @return the PDF bytes, or null if the file is missing or expired
     */
    public byte[] retrieve(String fileId) {
        Path filePath = storageDir.resolve(fileId + ".pdf");
        if (!Files.exists(filePath)) {
            return null;
        }
        try {
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            log.error("Failed to read report file: {}", fileId, e);
            return null;
        }
    }

    /**
     * Returns the download URL path for a stored file.
     */
    public String getDownloadUrl(String fileId) {
        return "/api/reports/download/" + fileId;
    }

    /**
     * Scheduled cleanup: removes files older than 1 hour.
     * Runs every 15 minutes (900000 ms).
     */
    @Scheduled(fixedRate = 900000)
    public void cleanupExpiredFiles() {
        if (!Files.exists(storageDir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(storageDir)) {
            Instant cutoff = Instant.now().minusMillis(ONE_HOUR_MILLIS);
            for (Path file : stream) {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                    if (attrs.creationTime().toInstant().isBefore(cutoff)) {
                        Files.delete(file);
                        log.info("Cleaned up expired report file: {}", file.getFileName());
                    }
                } catch (IOException e) {
                    log.warn("Failed to check/delete report file: {}", file.getFileName(), e);
                }
            }
        } catch (IOException e) {
            log.error("Failed to list report storage directory for cleanup", e);
        }
    }
}
