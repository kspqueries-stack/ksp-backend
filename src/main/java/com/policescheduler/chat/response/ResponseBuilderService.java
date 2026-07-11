package com.policescheduler.chat.response;

import com.policescheduler.chat.model.ProcessingContext;
import com.policescheduler.chat.model.ToolExecutionResult;
import com.policescheduler.dto.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ResponseBuilderService {

    private static final Logger log = LoggerFactory.getLogger(ResponseBuilderService.class);

    private final TemplateEngine templateEngine;
    private final PiiMaskingService piiMaskingService;
    private final TranslationService translationService;

    public ResponseBuilderService(TemplateEngine templateEngine,
                                  PiiMaskingService piiMaskingService,
                                  TranslationService translationService) {
        this.templateEngine = templateEngine;
        this.piiMaskingService = piiMaskingService;
        this.translationService = translationService;
    }

    public ChatResponse buildResponse(List<ToolExecutionResult> results, ProcessingContext context) {
        if (results == null || results.isEmpty()) {
            ChatResponse empty = ChatResponse.builder()
                    .response("No results available.")
                    .responseType("text")
                    .build();
            return applyPostProcessing(empty, context);
        }

        ChatResponse response;
        if (results.size() == 1) {
            response = templateEngine.format(results.get(0));
        } else {
            response = buildMultiResultResponse(results);
        }

        return applyPostProcessing(response, context);
    }

    private ChatResponse buildMultiResultResponse(List<ToolExecutionResult> results) {
        StringBuilder combined = new StringBuilder();
        Object lastData = null;
        String lastResponseType = "text";

        for (int i = 0; i < results.size(); i++) {
            ToolExecutionResult result = results.get(i);
            ChatResponse partial = templateEngine.format(result);

            if (results.size() > 1) {
                combined.append("--- Result ").append(i + 1).append(": ").append(result.toolName()).append(" ---\n");
            }
            combined.append(partial.getResponse()).append("\n");

            lastData = partial.getData();
            lastResponseType = partial.getResponseType();
        }

        // For multi-result, if all are tables, keep the last one as data
        // Otherwise, combine as text
        if (results.size() > 1) {
            return ChatResponse.builder()
                    .response(combined.toString().trim())
                    .responseType(lastResponseType)
                    .data(lastData)
                    .build();
        }

        return ChatResponse.builder()
                .response(combined.toString().trim())
                .responseType(lastResponseType)
                .data(lastData)
                .build();
    }

    private ChatResponse applyPostProcessing(ChatResponse response, ProcessingContext context) {
        // 1. PII masking
        response = piiMaskingService.maskPii(response, context.getUserRole());

        // 2. Translation (if Kannada)
        String responseLanguage = context.getResponseLanguage();
        if ("kn".equals(responseLanguage)) {
            response = translationService.translateResponse(response, "kn");
        }

        // 3. Set language field
        response.setLanguage(responseLanguage != null ? responseLanguage : "en");

        return response;
    }
}
