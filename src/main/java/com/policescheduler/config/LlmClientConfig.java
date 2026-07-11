package com.policescheduler.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration that creates the appropriate LLM client based on the AI provider setting.
 *
 * - "groq": Uses Groq's OpenAI-compatible API (for local development)
 * - "bedrock": Uses AWS Bedrock SDK with IAM role auth (for production on EC2)
 */
@Configuration
public class LlmClientConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmClientConfig.class);

    @Bean
    public LlmClient llmClient(
            @Value("${app.ai-provider:groq}") String aiProvider,
            @Value("${app.groq.api-key:}") String groqApiKey,
            @Value("${app.groq.api-url:https://api.groq.com/openai/v1}") String groqApiUrl,
            @Value("${app.groq.model:llama-3.3-70b-versatile}") String groqModel,
            @Value("${app.bedrock.region:us-east-1}") String bedrockRegion,
            @Value("${app.bedrock.model-id:amazon.nova-lite-v1:0}") String bedrockModelId,
            RestTemplate restTemplate) {

        if ("bedrock".equalsIgnoreCase(aiProvider)) {
            log.info("Initializing Bedrock LLM client | Region: {} | Model: {}", bedrockRegion, bedrockModelId);
            return new BedrockLlmClient(bedrockRegion, bedrockModelId);
        } else {
            log.info("Initializing Groq LLM client | Model: {} | URL: {}", groqModel, groqApiUrl);
            return new GroqLlmClient(restTemplate, groqApiKey, groqApiUrl, groqModel);
        }
    }
}
