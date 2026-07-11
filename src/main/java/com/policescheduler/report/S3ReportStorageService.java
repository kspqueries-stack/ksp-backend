package com.policescheduler.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class S3ReportStorageService {

    private static final Logger log = LoggerFactory.getLogger(S3ReportStorageService.class);
    private static final DateTimeFormatter DATE_PATH_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Value("${app.s3.bucket}")
    private String bucket;

    @Value("${app.s3.region}")
    private String region;

    private S3Client s3Client;

    @PostConstruct
    public void init() {
        this.s3Client = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * Uploads report bytes to S3.
     * Key format: reports/{reportType}/{yyyy-MM-dd}/{filename}
     *
     * @return the S3 key of the uploaded object
     */
    public String upload(String reportType, String filename, byte[] data, String contentType) {
        String s3Key = buildKey(reportType, filename);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .contentType(contentType)
                .contentLength((long) data.length)
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(data));
        log.info("Uploaded report to S3: {}", s3Key);
        return s3Key;
    }

    /**
     * Downloads a report from S3 by its key.
     */
    public byte[] download(String s3Key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();

        return s3Client.getObjectAsBytes(request).asByteArray();
    }

    private String buildKey(String reportType, String filename) {
        String datePart = LocalDate.now().format(DATE_PATH_FMT);
        return String.format("reports/%s/%s/%s", reportType, datePart, filename);
    }
}
