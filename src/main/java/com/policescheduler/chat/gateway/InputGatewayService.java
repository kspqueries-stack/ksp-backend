package com.policescheduler.chat.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.policescheduler.chat.executor.McpHost;
import com.policescheduler.chat.model.ActionPlan;
import com.policescheduler.chat.model.ProcessingContext;
import com.policescheduler.chat.model.ToolExecutionResult;
import com.policescheduler.chat.resilience.CircuitBreakerManager;
import com.policescheduler.chat.resilience.ResponseCacheService;
import com.policescheduler.chat.response.ResponseBuilderService;
import com.policescheduler.chat.router.FunctionCallingService;
import com.policescheduler.dto.ChatResponse;
import com.policescheduler.dto.CreatePersonnelRequest;
import com.policescheduler.dto.CreateLeaveRequest;
import com.policescheduler.dto.CycleCreateRequest;
import com.policescheduler.dto.CycleResponse;
import com.policescheduler.dto.PersonnelDto;
import com.policescheduler.dto.PlatoonSectionMapping;
import com.policescheduler.dto.chat.ConfirmationResponseData;
import com.policescheduler.dto.chat.FormSubmitRequest;
import com.policescheduler.dto.chat.SuggestionsResponseData;
import com.policescheduler.entity.ChatMessage;
import com.policescheduler.repository.ChatMessageRepository;
import com.policescheduler.service.CycleService;
import com.policescheduler.service.AdhocDutyService;
import com.policescheduler.dto.AdhocDutyResponse;
import com.policescheduler.dto.CreateAdhocDutyRequest;
import com.policescheduler.service.PersonnelService;
import com.policescheduler.service.LeaveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.Arrays;

@Service
public class InputGatewayService {

    private static final Logger log = LoggerFactory.getLogger(InputGatewayService.class);

    private final InputSanitizerService sanitizerService;
    private final LanguageDetectorService languageDetectorService;
    private final FunctionCallingService functionCallingService;
    private final McpHost mcpHost;
    private final ResponseBuilderService responseBuilderService;
    private final ResponseCacheService responseCacheService;
    private final CircuitBreakerManager circuitBreakerManager;
    private final ChatMessageRepository chatMessageRepository;
    private final ObjectMapper objectMapper;
    private final PersonnelService personnelService;
    private final LeaveService leaveService;
    private final CycleService cycleService;
    private final AdhocDutyService adhocDutyService;
    private final SttService sttService;
    private final DocumentProcessorService documentProcessorService;

    public InputGatewayService(InputSanitizerService sanitizerService,
                               LanguageDetectorService languageDetectorService,
                               FunctionCallingService functionCallingService,
                               McpHost mcpHost,
                               ResponseBuilderService responseBuilderService,
                               ResponseCacheService responseCacheService,
                               CircuitBreakerManager circuitBreakerManager,
                               ChatMessageRepository chatMessageRepository,
                               ObjectMapper objectMapper,
                               PersonnelService personnelService,
                               LeaveService leaveService,
                               CycleService cycleService,
                               AdhocDutyService adhocDutyService,
                               SttService sttService,
                               DocumentProcessorService documentProcessorService) {
        this.sanitizerService = sanitizerService;
        this.languageDetectorService = languageDetectorService;
        this.functionCallingService = functionCallingService;
        this.mcpHost = mcpHost;
        this.responseBuilderService = responseBuilderService;
        this.responseCacheService = responseCacheService;
        this.circuitBreakerManager = circuitBreakerManager;
        this.chatMessageRepository = chatMessageRepository;
        this.objectMapper = objectMapper;
        this.personnelService = personnelService;
        this.leaveService = leaveService;
        this.cycleService = cycleService;
        this.adhocDutyService = adhocDutyService;
        this.sttService = sttService;
        this.documentProcessorService = documentProcessorService;
    }

    public ChatResponse processTextMessage(Long userId, String userRole, String message) {
        long startTime = System.currentTimeMillis();
        String sessionId = UUID.randomUUID().toString();

        try {
            // 1. Sanitize input (keep for security)
            InputSanitizerService.SanitizeResult sanitized = sanitizerService.sanitize(message);
            if (sanitized.promptInjectionDetected()) {
                log.warn("Prompt injection detected for user {}", userId);
                return ChatResponse.builder()
                        .response("Your message could not be processed. Please rephrase your request.")
                        .responseType("text").language("en").build();
            }

            // 2. Detect language (needed to set response language)
            LanguageDetectorService.LanguageDetectionResult langResult = languageDetectorService.detect(sanitized.sanitizedText());
            String responseLanguage = langResult.responseLanguage();

            // 3. Build ProcessingContext (minimal — no conversation history resolution)
            ProcessingContext context = ProcessingContext.builder()
                    .userId(userId).userRole(userRole).sessionId(sessionId)
                    .detectedLanguage(langResult.detectedLanguage())
                    .responseLanguage(responseLanguage)
                    .originalInput(message).normalizedInput(langResult.canonicalEnglish())
                    .conversationHistory(List.of()).build();

            // 4. LLM Function Calling — ALL queries go to LLM for intent resolution
            ActionPlan plan;
            try {
                plan = functionCallingService.resolveIntent(message, context);
                log.info("LLM resolved: tool={}, intent={}, params={}", plan.toolName(), plan.intent(), plan.parameters());
            } catch (Exception e) {
                log.warn("Function calling failed: {}", e.getMessage());
                plan = ActionPlan.unknown(message, langResult.canonicalEnglish());
            }

            // 5. Execute tool via MCP Host
            List<ToolExecutionResult> results;
            if (plan.subPlans() != null && !plan.subPlans().isEmpty()) {
                results = mcpHost.dispatchAll(plan.subPlans(), context);
            } else if ("direct_response".equals(plan.toolName())) {
                // LLM chose to respond directly (greetings, clarifications, unsupported features)
                String directText = (String) plan.parameters().getOrDefault("direct_response", "How can I help you?");
                // Wrap with suggestions so user gets clickable options
                Object responseData = SuggestionsResponseData.builder()
                        .message(directText)
                        .suggestions(List.of("Show personnel list", "Show duty types", "Show leave status", "Show schedule", "Show drivers"))
                        .build();
                results = List.of(ToolExecutionResult.success("direct_response", responseData, 0));
            } else if (plan.toolName() != null && !plan.toolName().isBlank()) {
                // LLM selected a tool — dispatch it regardless of intent value
                results = List.of(mcpHost.dispatch(plan, context));
            } else {
                // No tool selected — LLM couldn't understand, provide helpful fallback with suggestions
                Object fallbackData = SuggestionsResponseData.builder()
                        .message("This feature is not supported yet. Try one of these options:")
                        .suggestions(List.of("Show personnel list", "Show duty types", "Create leave request", "Show schedule overview", "Show drivers", "Add duty type"))
                        .build();
                results = List.of(ToolExecutionResult.success("direct_response", fallbackData, 0));
            }

            // 6. Build response (includes translation of description to user's language)
            ChatResponse response = responseBuilderService.buildResponse(results, context);

            // 7. Save chat message (keep for history)
            saveChatMessage(userId, message, response.getResponse(), plan.intent());

            log.info("Chat processed in {}ms | tool={} | lang={}",
                    System.currentTimeMillis() - startTime, plan.toolName(), responseLanguage);

            return response;

        } catch (Exception e) {
            log.error("Error processing message for user {}", userId, e);
            return ChatResponse.builder()
                    .response("An error occurred processing your request. Please try again.")
                    .responseType("text").language("en").build();
        }
    }

    public ChatResponse processVoiceMessage(Long userId, String userRole, MultipartFile audioFile) {
        try {
            String transcribedText = circuitBreakerManager.execute("stt-service",
                    () -> sttService.transcribe(audioFile),
                    () -> null);

            if (transcribedText == null) {
                return ChatResponse.builder()
                        .response("Voice transcription is temporarily unavailable. Please type your message instead.")
                        .responseType("text").language("en").build();
            }

            return processTextMessage(userId, userRole, transcribedText);
        } catch (SttException e) {
            log.error("STT failed for user {}: {}", userId, e.getMessage());
            return ChatResponse.builder()
                    .response("Could not transcribe audio: " + e.getMessage() + ". Please type your message instead.")
                    .responseType("text").language("en").build();
        } catch (Exception e) {
            log.error("Voice processing failed for user {}", userId, e);
            return ChatResponse.builder()
                    .response("Voice processing failed. Please type your message instead.")
                    .responseType("text").language("en").build();
        }
    }

    public ChatResponse processDocumentUpload(Long userId, MultipartFile pdfFile) {
        return documentProcessorService.processUpload(userId, pdfFile);
    }

    public ChatResponse processFormSubmission(Long userId, String userRole, FormSubmitRequest request) {
        String action = request.getSubmitAction();
        if (action == null || action.isBlank()) {
            return ChatResponse.builder()
                    .response("Invalid form submission: no submit action specified.")
                    .responseType("confirmation")
                    .data(ConfirmationResponseData.builder()
                            .success(false)
                            .message("Invalid form submission: no submit action specified.")
                            .build())
                    .build();
        }

        return switch (action) {
            case "create_person" -> handleCreatePerson(request);
            case "create_leave" -> handleCreateLeave(request);
            case "create_cycle" -> handleCreateCycle(request);
            case "create_adhoc_duty" -> handleCreateAdhocDuty(request);
            default -> ChatResponse.builder()
                    .response("Unknown submit action: " + action)
                    .responseType("confirmation")
                    .data(ConfirmationResponseData.builder()
                            .success(false)
                            .message("Unknown submit action: " + action)
                            .build())
                    .build();
        };
    }

    private ChatResponse handleCreatePerson(FormSubmitRequest request) {
        Map<String, String> fields = request.getFields();
        if (fields == null) {
            fields = Map.of();
        }

        List<String> missingFields = new ArrayList<>();
        if (isBlank(fields.get("personName"))) missingFields.add("personName");
        if (isBlank(fields.get("badgeId"))) missingFields.add("badgeId");
        if (isBlank(fields.get("section"))) missingFields.add("section");

        if (!missingFields.isEmpty()) {
            String errorMsg = "Required fields missing: " + String.join(", ", missingFields);
            return ChatResponse.builder()
                    .response(errorMsg)
                    .responseType("confirmation")
                    .data(ConfirmationResponseData.builder()
                            .success(false).message(errorMsg).build())
                    .build();
        }

        CreatePersonnelRequest createReq = CreatePersonnelRequest.builder()
                .personName(fields.get("personName"))
                .badgeId(fields.get("badgeId"))
                .designation(fields.get("designation"))
                .section(fields.get("section"))
                .dutyType(fields.get("dutyType"))
                .location(fields.get("location"))
                .phoneNumber(fields.get("phoneNumber"))
                .email(fields.get("email"))
                .vehicleNumber(fields.get("vehicleNumber"))
                .dutyLocation(fields.get("dutyLocation"))
                .pdms(fields.get("pdms"))
                .licenceType(fields.get("licenceType"))
                .deployedFrom(fields.get("deployedFrom"))
                .build();

        try {
            PersonnelDto created = personnelService.create(createReq);

            Map<String, String> details = new LinkedHashMap<>();
            details.put("Name", created.getPersonName());
            details.put("Badge ID", created.getBadgeId());
            details.put("Designation", created.getDesignation() != null ? created.getDesignation() : "—");
            details.put("Section", created.getSection());

            return ChatResponse.builder()
                    .response("Personnel record created successfully for " + created.getPersonName() + ".")
                    .responseType("confirmation")
                    .data(ConfirmationResponseData.builder()
                            .success(true)
                            .message("Personnel record created successfully for " + created.getPersonName() + ".")
                            .details(details).build())
                    .build();
        } catch (Exception e) {
            log.error("Error creating personnel from form submission", e);
            return ChatResponse.builder()
                    .response("Failed to create personnel: " + e.getMessage())
                    .responseType("confirmation")
                    .data(ConfirmationResponseData.builder()
                            .success(false)
                            .message("Failed to create personnel: " + e.getMessage())
                            .build())
                    .build();
        }
    }

    private ChatResponse handleCreateLeave(FormSubmitRequest request) {
        Map<String, String> fields = request.getFields();
        if (fields == null) {
            fields = Map.of();
        }

        // Validate required fields
        List<String> missingFields = new ArrayList<>();
        if (isBlank(fields.get("personnelId"))) missingFields.add("personnelId");
        if (isBlank(fields.get("leaveType"))) missingFields.add("leaveType");
        if (isBlank(fields.get("startDate"))) missingFields.add("startDate");
        if (isBlank(fields.get("endDate"))) missingFields.add("endDate");

        if (!missingFields.isEmpty()) {
            String errorMsg = "Required fields missing: " + String.join(", ", missingFields);
            return ChatResponse.builder()
                    .response(errorMsg)
                    .responseType("confirmation")
                    .data(ConfirmationResponseData.builder()
                            .success(false).message(errorMsg).build())
                    .build();
        }

        try {
            // Extract personnelId — the select value is formatted as "id - name (badge)"
            String personnelIdStr = fields.get("personnelId");
            Long personnelId;
            if (personnelIdStr.contains(" - ")) {
                personnelId = Long.parseLong(personnelIdStr.split(" - ")[0].trim());
            } else {
                personnelId = Long.parseLong(personnelIdStr.trim());
            }

            CreateLeaveRequest createReq = CreateLeaveRequest.builder()
                    .personnelId(personnelId)
                    .leaveType(fields.get("leaveType"))
                    .startDate(LocalDate.parse(fields.get("startDate")))
                    .endDate(LocalDate.parse(fields.get("endDate")))
                    .reason(fields.get("reason"))
                    .build();

            var leaveDto = leaveService.submit(createReq);

            // Invalidate leave-related cache
            responseCacheService.invalidateForDomain("create_leave");

            String personnelName = leaveDto.getPersonnelName() != null ? leaveDto.getPersonnelName() : "ID " + personnelId;
            String successMsg = "Leave request created successfully for " + personnelName
                    + " from " + leaveDto.getStartDate() + " to " + leaveDto.getEndDate() + ".";

            Map<String, String> details = new LinkedHashMap<>();
            details.put("Personnel", personnelName);
            details.put("Leave Type", leaveDto.getLeaveType());
            details.put("Start Date", String.valueOf(leaveDto.getStartDate()));
            details.put("End Date", String.valueOf(leaveDto.getEndDate()));
            details.put("Status", leaveDto.getStatus());

            return ChatResponse.builder()
                    .response(successMsg)
                    .responseType("confirmation")
                    .data(ConfirmationResponseData.builder()
                            .success(true)
                            .message(successMsg)
                            .details(details).build())
                    .build();
        } catch (IllegalStateException e) {
            String errorMsg = "Failed to create leave request: " + e.getMessage();
            return ChatResponse.builder()
                    .response(errorMsg)
                    .responseType("confirmation")
                    .data(ConfirmationResponseData.builder()
                            .success(false)
                            .message(errorMsg)
                            .build())
                    .build();
        } catch (jakarta.persistence.EntityNotFoundException e) {
            String errorMsg = "Failed to create leave request: " + e.getMessage();
            return ChatResponse.builder()
                    .response(errorMsg)
                    .responseType("confirmation")
                    .data(ConfirmationResponseData.builder()
                            .success(false)
                            .message(errorMsg)
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("Error creating leave from form submission", e);
            return ChatResponse.builder()
                    .response("Failed to create leave request: " + e.getMessage())
                    .responseType("confirmation")
                    .data(ConfirmationResponseData.builder()
                            .success(false)
                            .message("Failed to create leave request: " + e.getMessage())
                            .build())
                    .build();
        }
    }

    private ChatResponse handleCreateCycle(FormSubmitRequest request) {
        Map<String, String> fields = request.getFields();
        if (fields == null) fields = Map.of();

        String startDateStr = fields.get("startDate");
        String rotationDaysStr = fields.get("rotationDays");

        if (isBlank(startDateStr) || isBlank(rotationDaysStr)) {
            String errorMsg = "Start date and rotation days are required.";
            return ChatResponse.builder()
                    .response(errorMsg)
                    .responseType("confirmation")
                    .data(ConfirmationResponseData.builder().success(false).message(errorMsg).build())
                    .build();
        }

        try {
            LocalDate startDate = LocalDate.parse(startDateStr);
            int rotationDays = Integer.parseInt(rotationDaysStr);

            // Build platoon-section mappings from form fields
            // Section values can be either numeric IDs ("3") or names ("Section C")
            List<PlatoonSectionMapping> platoonSections = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                String sectionValue = fields.get("platoon_" + i + "_sections");
                if (sectionValue == null || sectionValue.isBlank()) {
                    return ChatResponse.builder()
                            .response("Each platoon must have at least one section assigned.")
                            .responseType("confirmation")
                            .data(ConfirmationResponseData.builder().success(false)
                                    .message("Platoon " + i + " has no sections assigned.").build())
                            .build();
                }
                Long sectionId = parseSectionValue(sectionValue.trim());
                if (sectionId == null) {
                    return ChatResponse.builder()
                            .response("Invalid section value for Platoon " + i + ": " + sectionValue)
                            .responseType("confirmation")
                            .data(ConfirmationResponseData.builder().success(false)
                                    .message("Invalid section: " + sectionValue).build())
                            .build();
                }
                platoonSections.add(new PlatoonSectionMapping((long) i, List.of(sectionId)));
            }

            CycleCreateRequest createReq = new CycleCreateRequest(startDate, rotationDays, platoonSections);
            CycleResponse response = cycleService.createCycle(createReq);

            Map<String, String> details = new LinkedHashMap<>();
            details.put("Cycle ID", String.valueOf(response.id()));
            details.put("Start Date", String.valueOf(response.startDate()));
            details.put("End Date", String.valueOf(response.endDate()));
            details.put("Rotation Days", String.valueOf(response.rotationDays()));
            details.put("Status", response.status());

            String successMsg = "Platoon rotation cycle created successfully from "
                    + response.startDate() + " to " + response.endDate() + " (" + response.rotationDays() + " days).";

            return ChatResponse.builder()
                    .response(successMsg)
                    .responseType("confirmation")
                    .data(ConfirmationResponseData.builder()
                            .success(true).message(successMsg).details(details).build())
                    .build();
        } catch (NumberFormatException e) {
            return ChatResponse.builder()
                    .response("Invalid number format in form fields.")
                    .responseType("confirmation")
                    .data(ConfirmationResponseData.builder().success(false)
                            .message("Invalid number format: " + e.getMessage()).build())
                    .build();
        } catch (Exception e) {
            log.error("Error creating cycle from form submission", e);
            return ChatResponse.builder()
                    .response("Failed to create cycle: " + e.getMessage())
                    .responseType("confirmation")
                    .data(ConfirmationResponseData.builder().success(false)
                            .message("Failed to create cycle: " + e.getMessage()).build())
                    .build();
        }
    }

    private ChatResponse handleCreateAdhocDuty(FormSubmitRequest request) {
        Map<String, String> fields = request.getFields();
        if (fields == null) fields = Map.of();

        String dutyName = fields.get("duty_name");
        String dateStr = fields.get("date");
        String startTimeStr = fields.get("start_time");
        String endTimeStr = fields.get("end_time");
        String countStr = fields.get("required_count");
        String location = fields.get("location");
        String pickFrom = fields.get("pick_from");
        String personnelIdsStr = fields.get("personnel_ids");

        if (isBlank(dutyName) || isBlank(dateStr) || isBlank(startTimeStr) || isBlank(endTimeStr) || isBlank(countStr)) {
            String errorMsg = "Duty name, date, start time, end time, and required count are all required.";
            return ChatResponse.builder()
                    .response(errorMsg)
                    .responseType("confirmation")
                    .data(ConfirmationResponseData.builder().success(false).message(errorMsg).build())
                    .build();
        }

        try {
            LocalDate date = LocalDate.parse(dateStr);
            LocalTime startTime = LocalTime.parse(startTimeStr);
            LocalTime endTime = LocalTime.parse(endTimeStr);
            int requiredCount = Integer.parseInt(countStr);

            // Parse optional personnel_ids (comma-separated string)
            List<Long> personnelIds = null;
            if (!isBlank(personnelIdsStr)) {
                personnelIds = java.util.Arrays.stream(personnelIdsStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .collect(java.util.stream.Collectors.toList());
            }

            CreateAdhocDutyRequest createReq = new CreateAdhocDutyRequest(
                dutyName, date, startTime, endTime, requiredCount,
                isBlank(location) ? null : location,
                isBlank(pickFrom) ? "ALL_PLATOONS" : pickFrom,
                null,
                personnelIds
            );

            AdhocDutyResponse response = adhocDutyService.createAdhocDuty(createReq);

            Map<String, String> details = new LinkedHashMap<>();
            details.put("Adhoc Duty ID", String.valueOf(response.id()));
            details.put("Name", response.dutyName());
            details.put("Date", String.valueOf(response.date()));
            details.put("Time", response.startTime() + " - " + response.endTime());
            details.put("Personnel", response.actualCount() + "/" + response.requiredCount());
            details.put("Status", response.status());
            if (response.location() != null) details.put("Location", response.location());

            String successMsg = "Adhoc duty '" + response.dutyName() + "' created successfully with "
                    + response.actualCount() + " personnel assigned on " + response.date() + ".";

            return ChatResponse.builder()
                    .response(successMsg)
                    .responseType("confirmation")
                    .data(ConfirmationResponseData.builder()
                            .success(true).message(successMsg).details(details).build())
                    .build();
        } catch (NumberFormatException e) {
            return ChatResponse.builder()
                    .response("Invalid number for required count.")
                    .responseType("confirmation")
                    .data(ConfirmationResponseData.builder().success(false)
                            .message("Invalid number: " + e.getMessage()).build())
                    .build();
        } catch (Exception e) {
            log.error("Error creating adhoc duty from form submission", e);
            return ChatResponse.builder()
                    .response("Failed to create adhoc duty: " + e.getMessage())
                    .responseType("confirmation")
                    .data(ConfirmationResponseData.builder().success(false)
                            .message("Failed: " + e.getMessage()).build())
                    .build();
        }
    }

    private void saveChatMessage(Long userId, String userMessage, String aiResponse, String commandType) {
        try {
            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setUserId(userId);
            chatMessage.setUserMessage(userMessage);
            chatMessage.setAiResponse(aiResponse);
            chatMessage.setCommandType(commandType != null ? commandType.toUpperCase() : "GENERAL");
            chatMessageRepository.save(chatMessage);
        } catch (Exception e) {
            log.error("Failed to save chat message for user {}", userId, e);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Parses a section value from the form — handles both numeric IDs and section names.
     * Examples: "3", "Section C", "Section D", "3 - Section C"
     */
    private Long parseSectionValue(String value) {
        if (value == null || value.isBlank()) return null;

        // Try as plain number first
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {}

        // Map section names to IDs
        String upper = value.toUpperCase().trim();
        if (upper.contains("C")) return 3L;
        if (upper.contains("D")) return 4L;
        if (upper.contains("E")) return 5L;
        if (upper.contains("F")) return 6L;
        if (upper.contains("G")) return 7L;

        // Try "3 - C" or "3 - Section C" format
        if (value.contains(" - ")) {
            try {
                return Long.parseLong(value.split(" - ")[0].trim());
            } catch (NumberFormatException ignored) {}
        }

        return null;
    }
}
