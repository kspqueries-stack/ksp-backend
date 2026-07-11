package com.policescheduler.chat.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@Service
public class SttService {

    private static final Logger log = LoggerFactory.getLogger(SttService.class);

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "audio/wav", "audio/x-wav",
            "audio/mpeg", "audio/mp3",
            "audio/webm"
    );
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("wav", "mp3", "webm");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${groq.api.key:}")
    private String groqApiKey;

    @Value("${groq.stt.api.url:https://api.groq.com/openai/v1/audio/transcriptions}")
    private String sttApiUrl;

    @Value("${groq.stt.model:whisper-large-v3}")
    private String sttModel;

    public SttService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public String transcribe(MultipartFile audioFile) {
        validateFile(audioFile);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setBearerAuth(groqApiKey);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", audioFile.getResource());
            body.add("model", sttModel);

            HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    sttApiUrl, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String text = root.path("text").asText("");
                if (text.isBlank()) {
                    throw new SttException("Transcription returned empty text");
                }
                log.info("STT transcription successful, length={}", text.length());
                return text;
            }
            throw new SttException("STT API returned non-success status: " + response.getStatusCode());
        } catch (SttException e) {
            throw e;
        } catch (Exception e) {
            log.error("STT transcription failed", e);
            throw new SttException("Speech-to-text transcription failed: " + e.getMessage(), e);
        }
    }

    private void validateFile(MultipartFile audioFile) {
        if (audioFile == null || audioFile.isEmpty()) {
            throw new SttException("Audio file is empty or missing");
        }
        if (audioFile.getSize() > MAX_FILE_SIZE) {
            throw new SttException("Audio file exceeds maximum size of 10MB");
        }

        String contentType = audioFile.getContentType();
        String originalFilename = audioFile.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase()
                : "";

        boolean validType = contentType != null && ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase());
        boolean validExt = ALLOWED_EXTENSIONS.contains(extension);

        if (!validType && !validExt) {
            throw new SttException("Unsupported audio format. Accepted formats: WAV, MP3, WebM");
        }
    }
}
