package com.policescheduler.chat.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.policescheduler.dto.ChatResponse;
import com.policescheduler.dto.chat.FormResponseData;
import com.policescheduler.dto.chat.SuggestionsResponseData;
import com.policescheduler.dto.chat.TableResponseData;
import com.policescheduler.dto.chat.ConfirmationResponseData;
import com.policescheduler.entity.Translation;
import com.policescheduler.repository.TranslationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class TranslationService {

    private static final Logger log = LoggerFactory.getLogger(TranslationService.class);

    private static final Pattern PROPER_NOUN_PATTERN = Pattern.compile(
        "^[A-Z]{2,}[-\\d]*$|^[A-Z][a-z]+(?:\\s[A-Z][a-z]+)*$|^\\d+$|^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+$"
    );

    private final TranslationRepository translationRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${groq.api.key:}")
    private String groqApiKey;

    @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqApiUrl;

    public TranslationService(TranslationRepository translationRepository,
                              RestTemplate restTemplate,
                              ObjectMapper objectMapper) {
        this.translationRepository = translationRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public String translate(String text, String targetLocale) {
        if (text == null || text.isBlank() || "en".equals(targetLocale)) {
            return text;
        }
        if (looksLikeProperNoun(text)) {
            return text;
        }

        // Look up in DB
        Optional<Translation> cached = translationRepository.findByTranslationKeyAndLocale(text, targetLocale);
        if (cached.isPresent()) {
            return cached.get().getTranslationValue();
        }

        // Fallback to LLM
        String translated = translateViaLlm(text, targetLocale);
        if (translated != null && !translated.equals(text)) {
            cacheTranslation(text, targetLocale, translated);
        }
        return translated != null ? translated : text;
    }

    public ChatResponse translateResponse(ChatResponse response, String targetLocale) {
        if (response == null || "en".equals(targetLocale)) {
            return response;
        }

        response.setResponse(translate(response.getResponse(), targetLocale));

        Object data = response.getData();
        if (data instanceof TableResponseData tableData) {
            translateTable(tableData, targetLocale);
        } else if (data instanceof FormResponseData formData) {
            translateForm(formData, targetLocale);
        } else if (data instanceof SuggestionsResponseData sugData) {
            translateSuggestions(sugData, targetLocale);
        } else if (data instanceof ConfirmationResponseData confirmData) {
            confirmData.setMessage(translate(confirmData.getMessage(), targetLocale));
        }

        return response;
    }

    private void translateTable(TableResponseData tableData, String locale) {
        // Only translate column HEADERS — never translate row data from the database.
        // DB data (names, section codes, duty types) must remain as-is to avoid corruption.
        if (tableData.getColumns() != null) {
            tableData.getColumns().forEach(col -> col.setLabel(translate(col.getLabel(), locale)));
        }
        // DO NOT translate row data — it comes from the database and must stay in original form.
    }

    private void translateForm(FormResponseData formData, String locale) {
        formData.setTitle(translate(formData.getTitle(), locale));
        if (formData.getFields() != null) {
            formData.getFields().forEach(field -> field.setLabel(translate(field.getLabel(), locale)));
        }
    }

    private void translateSuggestions(SuggestionsResponseData sugData, String locale) {
        sugData.setMessage(translate(sugData.getMessage(), locale));
        if (sugData.getSuggestions() != null) {
            List<String> translated = sugData.getSuggestions().stream()
                    .map(s -> translate(s, locale))
                    .collect(Collectors.toList());
            sugData.setSuggestions(translated);
        }
    }

    private boolean looksLikeProperNoun(String text) {
        if (text == null || text.isBlank() || text.equals("—")) return true;
        return PROPER_NOUN_PATTERN.matcher(text.trim()).matches();
    }

    private String translateViaLlm(String text, String targetLocale) {
        try {
            String langName = "kn".equals(targetLocale) ? "Kannada" : targetLocale;
            Map<String, Object> requestBody = Map.of(
                "model", "llama-3.1-8b-instant",
                "messages", List.of(
                    Map.of("role", "system", "content",
                        "Translate the following text to " + langName + ". Return ONLY the translated text, nothing else."),
                    Map.of("role", "user", "content", text)
                ),
                "max_tokens", 256,
                "temperature", 0.0
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(groqApiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                groqApiUrl, HttpMethod.POST, entity, String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                return root.path("choices").get(0).path("message").path("content").asText(text);
            }
        } catch (Exception e) {
            log.error("LLM translation failed for text: {}", text, e);
        }
        return text;
    }

    private void cacheTranslation(String key, String locale, String value) {
        try {
            Translation translation = new Translation();
            translation.setTranslationKey(key);
            translation.setLocale(locale);
            translation.setTranslationValue(value);
            translation.setCategory("chat_auto");
            translationRepository.save(translation);
        } catch (Exception e) {
            log.warn("Failed to cache translation for key: {}", key, e);
        }
    }
}
