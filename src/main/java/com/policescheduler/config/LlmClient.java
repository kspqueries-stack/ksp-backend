package com.policescheduler.config;

import java.util.List;
import java.util.Map;

/**
 * Unified interface for LLM API calls.
 * Abstracts away the difference between Groq (OpenAI-compatible REST)
 * and AWS Bedrock (SDK-based) providers.
 */
public interface LlmClient {

    /**
     * Send a chat completion request and return the raw response body as a string.
     * The response follows OpenAI's chat completion format for consistency.
     *
     * @param model      the model identifier (ignored for Bedrock, uses configured model)
     * @param messages   list of message maps with "role" and "content" keys
     * @param tools      optional list of tool definitions (for function calling)
     * @param toolChoice optional tool choice constraint ("auto", "none", or specific)
     * @return raw JSON response string in OpenAI-compatible format
     */
    String chatCompletion(String model, List<Map<String, Object>> messages,
                          List<Map<String, Object>> tools, Object toolChoice);

    /**
     * Simple convenience method for chat without tools.
     */
    default String chatCompletion(String model, List<Map<String, Object>> messages) {
        return chatCompletion(model, messages, null, null);
    }

    /**
     * Returns the provider name for logging.
     */
    String getProviderName();
}
