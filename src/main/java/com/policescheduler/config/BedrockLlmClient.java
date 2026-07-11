package com.policescheduler.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import java.util.*;

/**
 * LLM client for AWS Bedrock (Amazon Nova Lite).
 * Uses the EC2 instance's IAM role for authentication — no API keys needed.
 *
 * Converts OpenAI-compatible request format to Bedrock's Converse format,
 * then converts the response back to OpenAI-compatible format for consistency.
 */
public class BedrockLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(BedrockLlmClient.class);

    private final BedrockRuntimeClient bedrockClient;
    private final String modelId;
    private final ObjectMapper objectMapper;

    public BedrockLlmClient(String region, String modelId) {
        this.modelId = modelId;
        this.objectMapper = new ObjectMapper();
        this.bedrockClient = BedrockRuntimeClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
        log.info("Bedrock LLM client initialized | Region: {} | Model: {}", region, modelId);
    }

    @Override
    public String chatCompletion(String model, List<Map<String, Object>> messages,
                                  List<Map<String, Object>> tools, Object toolChoice) {
        try {
            // Build the Bedrock request body in Amazon Nova's native format
            ObjectNode requestBody = buildBedrockRequest(messages, tools, toolChoice);
            String requestJson = objectMapper.writeValueAsString(requestBody);

            InvokeModelRequest invokeRequest = InvokeModelRequest.builder()
                    .modelId(modelId)
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromUtf8String(requestJson))
                    .build();

            InvokeModelResponse response = bedrockClient.invokeModel(invokeRequest);
            String responseJson = response.body().asUtf8String();

            // Convert Bedrock response to OpenAI-compatible format
            return convertToOpenAiFormat(responseJson, tools != null && !tools.isEmpty());

        } catch (Exception e) {
            log.error("Bedrock API call failed for model: {}", modelId, e);
            throw new RuntimeException("Bedrock API call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Build the request body for Amazon Nova models.
     * Nova uses the "messages" format similar to OpenAI but with some differences.
     */
    private ObjectNode buildBedrockRequest(List<Map<String, Object>> messages,
                                            List<Map<String, Object>> tools,
                                            Object toolChoice) {
        ObjectNode root = objectMapper.createObjectNode();

        // Separate system message from conversation messages
        String systemPrompt = null;
        List<Map<String, Object>> conversationMessages = new ArrayList<>();

        for (Map<String, Object> msg : messages) {
            String role = (String) msg.get("role");
            if ("system".equals(role)) {
                systemPrompt = (String) msg.get("content");
            } else {
                conversationMessages.add(msg);
            }
        }

        // System prompt goes in a separate field for Nova
        if (systemPrompt != null) {
            ArrayNode systemArray = root.putArray("system");
            ObjectNode systemMsg = systemArray.addObject();
            systemMsg.put("text", systemPrompt);
        }

        // Convert messages to Nova format
        ArrayNode messagesArray = root.putArray("messages");
        for (Map<String, Object> msg : conversationMessages) {
            ObjectNode msgNode = messagesArray.addObject();
            msgNode.put("role", (String) msg.get("role"));
            ArrayNode contentArray = msgNode.putArray("content");
            ObjectNode textContent = contentArray.addObject();
            textContent.put("text", (String) msg.get("content"));
        }

        // Inference configuration
        ObjectNode inferenceConfig = root.putObject("inferenceConfig");
        inferenceConfig.put("maxTokens", 4096);
        inferenceConfig.put("temperature", 0.1);

        // Tool configuration (if tools provided)
        if (tools != null && !tools.isEmpty()) {
            ObjectNode toolConfig = root.putObject("toolConfig");
            ArrayNode toolsArray = toolConfig.putArray("tools");

            for (Map<String, Object> tool : tools) {
                @SuppressWarnings("unchecked")
                Map<String, Object> function = (Map<String, Object>) tool.get("function");
                if (function != null) {
                    ObjectNode toolNode = toolsArray.addObject();
                    ObjectNode toolSpec = toolNode.putObject("toolSpec");
                    toolSpec.put("name", (String) function.get("name"));
                    toolSpec.put("description", (String) function.get("description"));

                    Object params = function.get("parameters");
                    if (params != null) {
                        toolSpec.set("inputSchema", objectMapper.createObjectNode()
                                .set("json", objectMapper.valueToTree(params)));
                    }
                }
            }

            // Tool choice
            if (toolChoice != null) {
                ObjectNode toolChoiceNode = toolConfig.putObject("toolChoice");
                if ("auto".equals(toolChoice)) {
                    toolChoiceNode.putObject("auto");
                } else if ("required".equals(toolChoice)) {
                    toolChoiceNode.putObject("any");
                }
            }
        }

        return root;
    }

    /**
     * Convert Bedrock's Nova response to OpenAI-compatible format.
     * This ensures all downstream code works without changes.
     */
    private String convertToOpenAiFormat(String bedrockResponse, boolean hasTools)
            throws JsonProcessingException {
        JsonNode response = objectMapper.readTree(bedrockResponse);

        ObjectNode openAiResponse = objectMapper.createObjectNode();
        openAiResponse.put("id", "bedrock-" + UUID.randomUUID().toString().substring(0, 8));
        openAiResponse.put("object", "chat.completion");
        openAiResponse.put("model", modelId);

        ArrayNode choices = openAiResponse.putArray("choices");
        ObjectNode choice = choices.addObject();
        choice.put("index", 0);
        choice.put("finish_reason", mapStopReason(response));

        ObjectNode message = choice.putObject("message");
        message.put("role", "assistant");

        // Extract content from Bedrock response
        JsonNode output = response.path("output");
        JsonNode outputMessage = output.path("message");
        JsonNode contentArray = outputMessage.path("content");

        if (contentArray.isArray() && !contentArray.isEmpty()) {
            StringBuilder textContent = new StringBuilder();
            ArrayNode toolCalls = objectMapper.createArrayNode();
            int toolCallIndex = 0;

            for (JsonNode contentItem : contentArray) {
                if (contentItem.has("text")) {
                    String text = contentItem.get("text").asText();
                    // Strip Bedrock's <thinking>...</thinking> tags from response
                    text = text.replaceAll("(?s)<thinking>.*?</thinking>", "").trim();
                    if (!text.isEmpty()) {
                        textContent.append(text);
                    }
                } else if (contentItem.has("toolUse")) {
                    JsonNode toolUse = contentItem.get("toolUse");
                    ObjectNode toolCall = toolCalls.addObject();
                    toolCall.put("id", "call_" + toolUse.path("toolUseId").asText());
                    toolCall.put("type", "function");
                    ObjectNode function = toolCall.putObject("function");
                    function.put("name", toolUse.path("name").asText());
                    function.put("arguments", objectMapper.writeValueAsString(toolUse.path("input")));
                    toolCallIndex++;
                }
            }

            if (textContent.length() > 0) {
                message.put("content", textContent.toString());
            } else {
                message.putNull("content");
            }

            if (!toolCalls.isEmpty()) {
                message.set("tool_calls", toolCalls);
            }
        } else {
            message.put("content", "");
        }

        // Usage info
        ObjectNode usage = openAiResponse.putObject("usage");
        JsonNode usageNode = response.path("usage");
        usage.put("prompt_tokens", usageNode.path("inputTokens").asInt(0));
        usage.put("completion_tokens", usageNode.path("outputTokens").asInt(0));
        usage.put("total_tokens",
                usageNode.path("inputTokens").asInt(0) + usageNode.path("outputTokens").asInt(0));

        return objectMapper.writeValueAsString(openAiResponse);
    }

    private String mapStopReason(JsonNode response) {
        String stopReason = response.path("stopReason").asText("end_turn");
        return switch (stopReason) {
            case "tool_use" -> "tool_calls";
            case "end_turn", "stop" -> "stop";
            case "max_tokens" -> "length";
            default -> "stop";
        };
    }

    @Override
    public String getProviderName() {
        return "bedrock";
    }
}
