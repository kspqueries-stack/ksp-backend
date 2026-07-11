package com.policescheduler.chat.router;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.policescheduler.chat.model.ActionPlan;
import com.policescheduler.chat.model.ProcessingContext;
import com.policescheduler.config.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * Function Calling Service - The core of the LLM architecture.
 * 
 * Uses the LlmClient interface to support both:
 * - Groq (local dev) via OpenAI-compatible REST
 * - AWS Bedrock (production) via SDK
 * 
 * Both providers return OpenAI-compatible JSON, so parsing is unified.
 * 
 * Flow:
 * 1. Understand user intent in ANY language (English, Kannada, mixed)
 * 2. Map it to the correct tool with proper parameters
 * 3. Return an ActionPlan for the executor to process
 */
@Service
public class FunctionCallingService {

    private static final Logger log = LoggerFactory.getLogger(FunctionCallingService.class);

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
            You are an AI assistant for CAR (City Armed Reserve) Police Station, Mangalore.
            You help police officers manage personnel, schedules, leave requests, and reports.
            
            CRITICAL RULES:
            1. ALWAYS call a tool. NEVER respond with just text when a tool can answer the question.
            2. NEVER ask clarification questions. Pick the most likely tool and execute it.
            3. Understand ALL languages: English, Kannada (ಕನ್ನಡ), Hindi, mixed.
            4. For ANY query about people/staff/personnel → call search_personnel or list_section_personnel
            5. For ANY query about leave/ರಜೆ → call get_leave_details (NOT get_leave_count unless specifically asking for counts only)
            6. For greetings (hi, hello, ನಮಸ್ಕಾರ), you may respond directly with a short greeting.
            7. If a query is ambiguous, prefer showing DATA over asking questions.
            8. For "download" / "ಡೌನ್‌ಲೋಡ್" requests → use the appropriate generate_*_pdf tool.
            
            BADGE ID RECOGNITION (CRITICAL):
            - Badge IDs follow format: PREFIX-NUMBER (e.g., AHC-2649, APC-168, RSI-01, DCP-01)
            - User may write badge without hyphen: "AHC 2649" = "AHC-2649"
            - User may omit prefix: "2649" likely means a badge search
            - When user says "details of AHC 2649" or "show AHC-2649" or "info about APC 168" → ALWAYS call get_person_by_badge(badge_id: "AHC-2649")
            - When user says "give details of 2649" → call get_person_by_badge(badge_id: "AHC-2649") (assume AHC prefix if number > 100)
            - NEVER interpret "AHC 2649" as designation search. AHC followed by a number is ALWAYS a badge ID.
            
            DOWNLOAD / REPORT RULES:
            - "download drivers" / "ಚಾಲಕರ ವರದಿ ಡೌನ್‌ಲೋಡ್" → generate_mt_section_pdf
            - "download section a" / "ವಿಭಾಗ A ವರದಿ" → generate_section_a_pdf
            - "download leave report" / "ರಜೆ ವರದಿ" → generate_leave_statement_pdf
            - "download form 168" / "ದೈನಿಕ ಹೇಳಿಕೆ" → generate_form_168_pdf
            - "download platoon chart" → generate_platoon_chart_pdf
            
            TOOL SELECTION RULES:
            - "change name" / "rename" / "update name" for badge X → update_personnel(badge_id: X, person_name: "new name")
            - "change designation" / "update rank" → update_personnel(badge_id: X, designation: "NEW_RANK")
            - "personnel list" / "staff list" / "ಸಿಬ್ಬಂದಿ ಪಟ್ಟಿ" → search_personnel(query: "")
            - "sick leave" / "ಅನಾರೋಗ್ಯ ರಜೆ" → get_leave_details(leave_type: "SICK_LEAVE")
            - "casual leave" / "ಸಾಮಾನ್ಯ ರಜೆ" → get_leave_details(leave_type: "CASUAL_LEAVE")
            - "leave status" / "ರಜೆ ಸ್ಥಿತಿ" → get_leave_details()
            - "how many on leave" / "ಎಷ್ಟು ರಜೆ" → get_leave_count()
            - "platoon" / "rotation" / "ಪ್ಲಟೂನ್" → get_platoon_rotation()
            - "section A/B/C" / "ವಿಭಾಗ" → list_section_personnel(section: X)
            - "drivers" / "ಚಾಲಕರು" → search_drivers()
            - "guard duty" / "ಗಾರ್ಡ್" → get_today_duties(duty_type: "GUARD")
            - "strength" / "ಸಂಖ್ಯೆ" / "how many" → get_strength_summary()
            - "duty types" / "show duties" / "posts" / "ಕರ್ತವ್ಯ ಪ್ರಕಾರಗಳು" → get_duty_types()
            - "add duty type" / "create duty type" / "new post" / "new duty" → add_duty_type (ALWAYS call this tool, NEVER say unsupported)
            - "duty types in section A" / "section A duties" → get_duty_types(section: "A")
            
            LEAVE CREATION RULES:
            - "create sick leave for AHC 2649 on june 22 2026" → create_leave(badge_id: "AHC-2649", leave_type: "SICK_LEAVE", start_date: "2026-06-22", end_date: "2026-06-22")
            - "casual leave for APC 168 tomorrow" → create_leave(badge_id: "APC-168", leave_type: "CASUAL_LEAVE", start_date: tomorrow, end_date: tomorrow)
            - "create sick leave for jayanth on 21 06 2026" → create_leave(badge_id: "jayanth", leave_type: "SICK_LEAVE", start_date: "2026-06-21", end_date: "2026-06-21")
            - "apply leave for AHC 2649" (no type mentioned) → respond with direct_response asking: "Which leave type? (Casual, Sick, Earned, Weekly Off, Compensatory Off)"
            - CRITICAL: When user says "create leave" or "apply leave" with a person name OR badge ID → ALWAYS call create_leave tool. NEVER call search_personnel.
            - Even if the name is ambiguous, call create_leave — the tool will show a form with matching personnel options.
            - The badge_id field accepts BOTH badge IDs (AHC-2649) AND names (jayanth, Mahantesh).
            - Convert date formats: "21 06 2026" = "2026-06-21", "june 22" = current year june 22.
            
            CYCLE MANAGEMENT RULES (CRITICAL - ALWAYS CALL THESE TOOLS):
            - "create platoon" / "create platoon cycle" / "create cycle" / "new cycle" / "setup rotation" / "create shift cycle" / "create schedule" / "publish cycle" → create_cycle (ALWAYS call this tool)
            - "list cycles" / "show cycles" / "view cycles" / "cycle history" → list_cycles
            - "cycle details" / "show cycle 5" → get_cycle_details(cycle_id: X)
            - "update cycle" / "modify cycle" → update_cycle(cycle_id: X, ...)
            - "delete cycle" / "remove cycle" → delete_cycle(cycle_id: X)
            - "reassign duty" / "reassign assignment" / "manually reassign" → reassign_duty(assignment_id: X, new_person_id: Y)
            - "auto reassign" / "auto assign" / "find replacement" / "auto swap" / "automatically reassign" → auto_reassign_duty(assignment_id: X)
            - NEVER say "create platoon" is unsupported. The create_cycle tool handles platoon creation.
            - For auto reassign, only assignment_id is needed — the system picks the replacement automatically.
            
            SCHEDULE/CURRENT DEPLOYMENT:
            - "who's working today" / "current schedule" / "show schedule" / "today's assignment" → get_schedule_overview()
            - "show current schedule" / "schedule overview" → get_schedule_overview()
            
            ADHOC/SPECIAL DUTY RULES:
            - "create adhoc duty" / "bandobast duty" / "special duty" / "create special assignment" → create_adhoc_duty
            - "list adhoc duties" / "show special duties" / "bandobast list" → list_adhoc_duties
            - "adhoc duty details" / "show adhoc duty 5" → get_adhoc_duty_details(adhoc_duty_id: X)
            - "cancel adhoc duty" / "cancel special duty" → cancel_adhoc_duty(adhoc_duty_id: X)
            - "preview personnel for adhoc" / "show available personnel" / "who is available for adhoc" → preview_adhoc_personnel
            - "search personnel for adhoc" / "find person for special duty" → search_adhoc_personnel
            - When user specifies personnel IDs for adhoc duty → pass them as personnel_ids to create_adhoc_duty
            - NEVER say adhoc duty creation is unsupported. The create_adhoc_duty tool handles it.
            
            SECTION MAPPING:
            - Section A = Fixed/Essential duties (Guards, Chamber Sentry, Gunman, Dog Squad)
            - Section B = Office/Support duties (Writers, Canteen, Police Lane)
            - Sections C, D, E, F, G = Rotational platoon duties (Guard I, Guard II, Striking Force, etc.)
            - Section MT = Motor Transport / Drivers
            - Section PMT = Present Strength
            
            DESIGNATION MAPPING (THESE ARE RANKS, NOT BADGE IDs):
            DCP, ACP, RPI, RSI, ARSI, AHC, APC
            - "list all AHC" / "give list of all AHC currently" → search_by_designation(designation: "AHC")
            - "AHC-2649" or "AHC 2649" (with a number) → get_person_by_badge(badge_id: "AHC-2649") — THIS IS A BADGE, NOT DESIGNATION
            
            CRITICAL DISTINCTION:
            - "AHC" alone (no number) = designation/rank → search_by_designation
            - "AHC 2649" or "AHC-2649" (with number) = specific badge ID → get_person_by_badge
            - "all AHC" = designation search → search_by_designation
            - "details of AHC 2649" = specific person → get_person_by_badge
            
            KANNADA TERMS:
            ರಜೆ=leave, ಅನಾರೋಗ್ಯ=sick, ಸಿಬ್ಬಂದಿ=personnel, ವೇಳಾಪಟ್ಟಿ=schedule,
            ಕರ್ತವ್ಯ=duty, ವರದಿ=report, ಎಷ್ಟು=how many, ತೋರಿಸು=show,
            ಪಟ್ಟಿ=list, ಚಾಲಕರು=drivers, ಪ್ಲಟೂನ್=platoon, ಗಾರ್ಡ್=guard,
            ವ್ಯಯಕ್ತಿಕ=personal, ವಿವರಗಳ=details, ಕರ್ತವ್ಯ ಪ್ರಕಾರಗಳು=duty types,
            ಹುದ್ದೆಗಳು=posts, ಡೌನ್‌ಲೋಡ್=download
            """;

    public FunctionCallingService(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        log.info("FunctionCallingService initialized with provider: {}", llmClient.getProviderName());
    }

    /**
     * LLM Call #1: Determine which tool to call and with what parameters.
     * Returns an ActionPlan with the tool name and parameters.
     */
    public ActionPlan resolveIntent(String userMessage, ProcessingContext context) {
        try {
            List<Map<String, Object>> messages = buildMessages(userMessage, context);
            List<Map<String, Object>> tools = buildToolDefinitions();

            String responseBody = llmClient.chatCompletion(null, messages, tools, "auto");

            if (responseBody != null && !responseBody.isBlank()) {
                return parseToolCallResponse(responseBody, userMessage, context.getNormalizedInput());
            }
        } catch (RuntimeException e) {
            // Handle Groq's known issue: 400 with failed_generation containing tool call
            // in <function=tool_name{args}</function> format
            String errorMsg = e.getMessage() != null ? e.getMessage() : "";
            if (errorMsg.contains("400") || errorMsg.contains("failed_generation")) {
                log.warn("LLM call failed, attempting to parse error for tool call: {}", errorMsg);
                ActionPlan parsed = tryParseFailedGeneration(errorMsg, userMessage, context.getNormalizedInput());
                if (parsed != null) return parsed;
            }

            // Also try for wrapped HttpClientErrorException
            Throwable cause = e.getCause();
            if (cause instanceof org.springframework.web.client.HttpClientErrorException.BadRequest badRequest) {
                String body = badRequest.getResponseBodyAsString();
                log.warn("Groq 400 with failed_generation, attempting to parse: {}", body);
                ActionPlan parsed = tryParseFailedGeneration(body, userMessage, context.getNormalizedInput());
                if (parsed != null) return parsed;
            }

            log.error("Function calling failed for input: {}", userMessage, e);
        } catch (Exception e) {
            log.error("Unexpected error in function calling for input: {}", userMessage, e);
        }

        return ActionPlan.unknown(userMessage, context.getNormalizedInput());
    }

    /**
     * Build the messages list for the LLM request.
     */
    private List<Map<String, Object>> buildMessages(String userMessage, ProcessingContext context) {
        List<Map<String, Object>> messages = new ArrayList<>();

        // System message
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));

        // Conversation history for context
        if (context.getConversationHistory() != null) {
            for (String historyMsg : context.getConversationHistory()) {
                messages.add(Map.of("role", "user", "content", historyMsg));
            }
        }

        // Current user message with language hint
        String langHint = "";
        if ("kn".equals(context.getResponseLanguage())) {
            langHint = "\n[RESPOND IN KANNADA (ಕನ್ನಡ)]";
        }
        messages.add(Map.of("role", "user", "content", userMessage + langHint));

        return messages;
    }

    /**
     * Build tool definitions in OpenAI-compatible format.
     * Both GroqLlmClient and BedrockLlmClient handle the conversion internally.
     */
    private List<Map<String, Object>> buildToolDefinitions() {
        List<Map<String, Object>> tools = new ArrayList<>();

        tools.add(buildTool("search_personnel",
                "Search for police personnel by name, badge ID, or any keyword. Use this when user asks to find someone specific or search across all personnel.",
                Map.of("query", prop("string", "The search term - name, badge ID, phone, email, or keyword. Use empty string to list all.")),
                List.of("query")));

        tools.add(buildTool("search_by_duty_type",
                "Search personnel filtered by their specific duty type. Examples: GUARD, COMPOL DUTY, DK SP DUTY, HOYSALA, HIGHWAY PATROL, TRAFFIC, CCB, STRIKING FORCE, CHECK POST, DOG SQUAD, ASC TEAM, GUNMAN, DRIVER",
                Map.of("duty_type", prop("string", "The duty type (e.g., GUARD, DRIVER, COMPOL DUTY, HOYSALA, TRAFFIC, CHECK POST, STRIKING FORCE, DOG SQUAD, CCB, GUNMAN)")),
                List.of("duty_type")));

        tools.add(buildTool("search_by_designation",
                "Search personnel by rank/designation. Use ONLY when user asks for a LIST of specific rank with NO number after it. Example: 'list all AHC', 'show RSI'. Do NOT use when user provides a badge number like 'AHC 2649' — use get_person_by_badge instead.",
                Map.of("designation", prop("string", "The designation (DCP, ACP, RPI, RSI, ARSI, AHC, APC)")),
                List.of("designation")));

        tools.add(buildTool("list_section_personnel",
                "List all personnel in a specific section. Use when user asks about a particular section or platoon. Section A=Fixed duties, B=Office/Support, C/D/E/F/G=Rotational platoons, MT=Drivers, PMT=Present Strength",
                Map.of("section", prop("string", "Section code (A, B, C, D, E, F, G, MT, PMT)")),
                List.of("section")));

        tools.add(buildTool("get_person_by_badge",
                "Get complete details about one specific person by their badge ID OR name. CRITICAL: Use this when user asks for details of a specific person. Works with badge IDs (AHC-2649, APC-168) AND names (Jayanth, Mahantesh). Examples: 'details of AHC 2649' → badge_id='AHC-2649'. 'get details of jayanth' → badge_id='jayanth'. 'show info about Mahantesh' → badge_id='Mahantesh'. If badge not found, automatically searches by name.",
                Map.of("badge_id", prop("string", "The badge ID (e.g., AHC-2649) OR person's name (e.g., Jayanth). Normalize badges: 'AHC 2649' → 'AHC-2649'")),
                List.of("badge_id")));

        tools.add(buildTool("get_schedule_overview",
                "Get current duty schedule overview showing assignment counts per section (A, B, C). Use for general schedule questions.",
                Map.of(), List.of()));

        tools.add(buildTool("get_leave_count",
                "Get leave request summary with counts by status (pending, approved, rejected). Use when user asks about leave status, how many on leave, leave summary.",
                Map.of(), List.of()));

        tools.add(buildTool("get_platoon_rotation",
                "Get current platoon rotation - which platoon is assigned to which duty (Guard-I, Guard-II, Check Post, Escort, Striking Force). Use when user asks about platoon duties, rotation, or specific platoon assignment.",
                Map.of(), List.of()));

        tools.add(buildTool("create_leave",
                "Create/apply for a new leave request. Use when user wants to submit a leave application. CRITICAL: If user says 'create sick leave for AHC 2649 on june 22' → call THIS tool with badge_id='AHC-2649', leave_type='SICK_LEAVE', start_date='2026-06-22'. Do NOT show personnel list. The badge_id can be a badge ID (like APC-035, AHC-2649) OR a person's name. IMPORTANT: Convert relative dates — 'today' = current date, 'tomorrow' = current date + 1. Today's date is " + LocalDate.now() + ". If user does NOT specify leave_type, do NOT guess — respond with direct_response asking which type they want (CASUAL_LEAVE, SICK_LEAVE, EARNED_LEAVE, WEEKLY_OFF, COMPENSATORY_OFF).",
                Map.of("badge_id", prop("string", "Badge ID (e.g., APC-035, AHC-2649) OR person name. Normalize badge: 'AHC 2649' → 'AHC-2649'"),
                        "leave_type", prop("string", "Type of leave (CASUAL_LEAVE, SICK_LEAVE, EARNED_LEAVE, WEEKLY_OFF, COMPENSATORY_OFF) - required, ASK user if not specified"),
                        "start_date", prop("string", "Start date in yyyy-MM-dd format. Convert 'today' to " + LocalDate.now() + ", 'tomorrow' to " + LocalDate.now().plusDays(1) + ", 'june 22 2026' to 2026-06-22"),
                        "end_date", prop("string", "End date in yyyy-MM-dd format. Same as start_date if single day leave."),
                        "reason", prop("string", "Reason for leave - optional")),
                List.of("badge_id", "leave_type", "start_date", "end_date")));

        tools.add(buildTool("add_person",
                "Add a new police personnel to the system. Use when user wants to register/add a new staff member. Create directly with provided details.",
                Map.of("person_name", prop("string", "Full name of the person (required)"),
                        "badge_id", prop("string", "Badge ID to assign (required)"),
                        "designation", prop("string", "Rank (DCP, ACP, RPI, RSI, ARSI, AHC, APC) - optional"),
                        "section", prop("string", "Section to assign (A, B, C, D, E, F, G, MT, PMT) (required)"),
                        "duty_type", prop("string", "Duty type - optional"),
                        "phone_number", prop("string", "Phone number - optional"),
                        "email", prop("string", "Email - optional"),
                        "location", prop("string", "Location - optional")),
                List.of("person_name", "badge_id", "section")));

        tools.add(buildTool("filter_personnel",
                "Filter personnel with combined criteria. Use when user asks for specific combination like 'AHC in Section A' or 'RSI on guard duty'.",
                Map.of("section", prop("string", "Section filter (A, B, C, D, E, F, G, MT, PMT) - optional"),
                        "designation", prop("string", "Designation filter (DCP, ACP, RPI, RSI, ARSI, AHC, APC) - optional"),
                        "duty_type", prop("string", "Duty type filter (GUARD, TRAFFIC, HOYSALA, etc.) - optional")),
                List.of()));

        tools.add(buildTool("get_leave_details",
                "Get detailed leave request list with names. Use when user asks 'who is on leave', 'show leaves this month', 'pending leave requests'.",
                Map.of("status", prop("string", "Filter by status (PENDING, APPROVED, REJECTED) - optional"),
                        "leave_type", prop("string", "Filter by type (CASUAL_LEAVE, SICK_LEAVE, EARNED_LEAVE) - optional"),
                        "start_date", prop("string", "Filter from date (yyyy-MM-dd) - optional"),
                        "end_date", prop("string", "Filter to date (yyyy-MM-dd) - optional")),
                List.of()));

        tools.add(buildTool("get_today_duties",
                "Get today's duty assignments filtered by duty type or section. Use for 'who is on guard today', 'today's check post', 'today's duties'.",
                Map.of("duty_type", prop("string", "Filter by duty type (GUARD, CHECK POST, STRIKING FORCE, etc.) - optional"),
                        "section", prop("string", "Filter by section (A, B, C, D, E, F, G) - optional")),
                List.of()));

        tools.add(buildTool("update_personnel",
                "Update a person's details by badge ID. Can update: name (person_name), section, duty_type, designation, phone_number, email, location, vehicle_number, pdms, licence_type, is_active. IMPORTANT: When user says 'change name' or 'rename' or 'update name' → set person_name. Do NOT set designation when user says 'name'.",
                Map.of("badge_id", prop("string", "Badge ID of person to update (required)"),
                        "person_name", prop("string", "New name for the person - use when user says 'change name', 'rename', 'update name' - optional"),
                        "section", prop("string", "New section to assign - optional"),
                        "duty_type", prop("string", "New duty type - optional"),
                        "designation", prop("string", "New designation/rank (DCP,ACP,RPI,RSI,ARSI,AHC,APC) - use ONLY when user explicitly says 'designation' or 'rank' - optional"),
                        "phone_number", prop("string", "New phone number - optional"),
                        "email", prop("string", "New email - optional"),
                        "location", prop("string", "New location - optional"),
                        "vehicle_number", prop("string", "New vehicle number - optional"),
                        "is_active", prop("string", "Set active status: true or false - optional")),
                List.of("badge_id")));

        tools.add(buildTool("get_strength_summary",
                "Get personnel count by designation for a section or overall. Use for 'total strength', 'how many in section A', 'strength summary'.",
                Map.of("section", prop("string", "Section to get strength for (A, B, C, MT, etc.) - leave empty for overall")),
                List.of()));

        tools.add(buildTool("search_drivers",
                "Search MT section drivers with filters for PDMS status, licence type, or vehicle. Use for 'drivers without PDMS', 'LMV drivers', 'HMV licence holders'.",
                Map.of("pdms", prop("string", "PDMS filter (DONE, NOT DONE) - optional"),
                        "licence_type", prop("string", "Licence type filter (LMV, HMV, LMV&HMV) - optional"),
                        "vehicle", prop("string", "Vehicle number search - optional")),
                List.of()));

        tools.add(buildTool("get_duty_types",
                "List all duty types/posts available in the system. Use when user asks 'show duty types', 'what duties are there', 'ಕರ್ತವ್ಯ ಪ್ರಕಾರಗಳು', 'list all posts'. Optionally filter by section.",
                Map.of("section", prop("string", "Section filter (A, B, C, D, E, F, G, MT) - optional")),
                List.of()));

        tools.add(buildTool("add_duty_type",
                "Add/create a new duty type/post. Use when user says 'add duty type', 'create duty type', 'new post', 'new duty', 'add duty', 'Add duty type'. ALWAYS call this tool for duty type creation — never say unsupported. If name/section are not provided, ask via direct_response.",
                Map.of("name", prop("string", "Name of the duty type (e.g., GUARD, PATROL, TRAFFIC) - optional, ask if missing"),
                        "section", prop("string", "Section to assign (A, B, C, D, E, F, G, MT) - optional, ask if missing"),
                        "latitude", prop("string", "Latitude for duty location - optional"),
                        "longitude", prop("string", "Longitude for duty location - optional"),
                        "radius_meters", prop("string", "Radius in meters - optional, default 500")),
                List.of()));

        tools.add(buildTool("update_duty_type",
                "Update an existing duty type. Use when user says 'update duty type', 'rename duty', 'change duty section'. Identify by name or ID.",
                Map.of("name", prop("string", "Current name of the duty type to update - required"),
                        "new_name", prop("string", "New name for the duty type - optional"),
                        "section", prop("string", "New section to move the duty type to - optional"),
                        "latitude", prop("string", "New latitude - optional"),
                        "longitude", prop("string", "New longitude - optional")),
                List.of("name")));

        tools.add(buildTool("delete_duty_type",
                "Delete a duty type/post. Use when user says 'delete duty type', 'remove duty', 'remove post'. Identify by name.",
                Map.of("name", prop("string", "Name of the duty type to delete - required")),
                List.of("name")));

        tools.add(buildTool("generate_mt_section_pdf",
                "Generate and download the MT Section (Drivers) PDF report. Use when user says 'download drivers', 'ಚಾಲಕರ ವರದಿ ಡೌನ್‌ಲೋಡ್'.",
                Map.of(), List.of()));

        tools.add(buildTool("generate_section_a_pdf",
                "Generate and download the Section A (Static Duties) PDF report. Use when user says 'download section a report'.",
                Map.of(), List.of()));

        tools.add(buildTool("generate_leave_statement_pdf",
                "Generate and download the Leave Statement PDF report. Use when user says 'download leave report', 'ರಜೆ ವರದಿ ಡೌನ್‌ಲೋಡ್'.",
                Map.of(), List.of()));

        tools.add(buildTool("generate_form_168_pdf",
                "Generate and download the Daily Statement (Form 168) PDF report. Use when user says 'download form 168', 'daily statement'.",
                Map.of(), List.of()));

        tools.add(buildTool("generate_platoon_chart_pdf",
                "Generate and download the Platoon Chart PDF report. Use when user says 'download platoon chart', 'rotational duties report'.",
                Map.of(), List.of()));

        // Cycle Management Tools
        tools.add(buildTool("create_cycle",
                "Create a new platoon rotation cycle. Use when user says 'create platoon', 'create cycle', 'create platoon cycle', 'create schedule', 'new cycle', 'setup rotation', 'publish cycle'. This tool ALWAYS works — never say it's unsupported. If parameters are missing, a form will be shown to the user.",
                Map.of("start_date", prop("string", "Start date in yyyy-MM-dd format - optional, form shown if missing"),
                        "rotation_days", prop("string", "Number of consecutive days for this rotation cycle - optional, form shown if missing"),
                        "platoon_sections", prop("string", "JSON array of platoon-section mappings - optional, form shown if missing")),
                List.of()));

        tools.add(buildTool("list_cycles",
                "List all platoon rotation cycles. Optionally filter by status (ACTIVE, COMPLETED, DELETED). Use when user says 'list cycles', 'show cycles', 'view rotation cycles', 'cycle history'.",
                Map.of("status", prop("string", "Filter by status (ACTIVE, COMPLETED, DELETED) - optional")),
                List.of()));

        tools.add(buildTool("get_cycle_details",
                "Get detailed information about a specific platoon rotation cycle including platoon-section mappings. Use when user asks about a specific cycle by ID.",
                Map.of("cycle_id", prop("string", "The cycle ID to get details for (required)")),
                List.of("cycle_id")));

        tools.add(buildTool("update_cycle",
                "Update an existing platoon rotation cycle. Can update start_date, rotation_days, platoon_sections, or status.",
                Map.of("cycle_id", prop("string", "The cycle ID to update (required)"),
                        "start_date", prop("string", "New start date in yyyy-MM-dd format - optional"),
                        "rotation_days", prop("string", "New rotation days - optional"),
                        "status", prop("string", "New status (ACTIVE, COMPLETED) - optional"),
                        "platoon_sections", prop("string", "New platoon-section mappings JSON array - optional")),
                List.of("cycle_id")));

        tools.add(buildTool("delete_cycle",
                "Soft-delete a platoon rotation cycle (sets status to DELETED). Use when user says 'delete cycle', 'remove cycle'.",
                Map.of("cycle_id", prop("string", "The cycle ID to delete (required)")),
                List.of("cycle_id")));

        tools.add(buildTool("reassign_duty",
                "Reassign a cycle duty assignment to a different person. Validates the replacement is not on leave. Use when user wants to manually reassign someone's duty to a specific person.",
                Map.of("assignment_id", prop("string", "The assignment ID to reassign (required)"),
                        "new_person_id", prop("string", "The person ID of the replacement (required)")),
                List.of("assignment_id", "new_person_id")));

        tools.add(buildTool("auto_reassign_duty",
                "Auto-reassign a duty assignment to a randomly available person from another platoon. Use when user says 'auto reassign', 'auto assign duty', 'find replacement for', 'auto swap duty'. Only needs the assignment ID — system picks the best available person automatically.",
                Map.of("assignment_id", prop("string", "The assignment ID to auto-reassign (required)")),
                List.of("assignment_id")));

        tools.add(buildTool("get_cycle_activities",
                "Get audit log / activity history for cycles. Shows all changes: reassignments, cycle creation, updates, deletions. Use when user says 'show activities', 'cycle history', 'audit log', 'what changed'. Optionally filter by a specific cycle.",
                Map.of("cycle_id", prop("string", "Filter by specific cycle ID - optional, leave empty for all activities")),
                List.of()));

        // Adhoc Duty Tools
        tools.add(buildTool("create_adhoc_duty",
                "Create an adhoc/special/bandobast duty. ALWAYS call this tool when user wants to create adhoc duty — it shows a form if parameters are missing. If user provides some details (name, date, count), pass them as parameters to pre-fill the form. If ALL parameters are provided AND user explicitly says to auto-pick, create directly. Supports manual personnel selection via personnel_ids parameter.",
                Map.of("duty_name", prop("string", "Name of the adhoc duty - optional, form shown if missing"),
                        "date", prop("string", "Date in yyyy-MM-dd format - optional. Convert 'today' to " + LocalDate.now() + ", 'tomorrow' to " + LocalDate.now().plusDays(1)),
                        "start_time", prop("string", "Start time in HH:mm format - optional"),
                        "end_time", prop("string", "End time in HH:mm format - optional"),
                        "required_count", prop("string", "Number of personnel needed - optional"),
                        "location", prop("string", "Duty location - optional"),
                        "pick_from", prop("string", "ALL_PLATOONS or SPECIFIC_PLATOONS - optional, defaults to ALL_PLATOONS"),
                        "personnel_ids", prop("string", "Comma-separated personnel IDs for manual selection (e.g., '1,5,12'). When provided, skips auto-pick. Use preview_adhoc_personnel or search_adhoc_personnel to find IDs first.")),
                List.of()));

        tools.add(buildTool("list_adhoc_duties",
                "List all adhoc/special duties. Optionally filter by date or status. Use when user says 'show adhoc duties', 'list special duties', 'bandobast list'.",
                Map.of("date", prop("string", "Filter by date in yyyy-MM-dd format - optional"),
                        "status", prop("string", "Filter by status: ACTIVE, COMPLETED, CANCELLED - optional")),
                List.of()));

        tools.add(buildTool("get_adhoc_duty_details",
                "Get full details of a specific adhoc duty including assigned personnel. Use when user asks about a specific adhoc duty by ID.",
                Map.of("adhoc_duty_id", prop("string", "The adhoc duty ID (required)")),
                List.of("adhoc_duty_id")));

        tools.add(buildTool("cancel_adhoc_duty",
                "Cancel an adhoc duty and release all assigned personnel back to normal duty. Use when user says 'cancel adhoc duty', 'cancel special duty'.",
                Map.of("adhoc_duty_id", prop("string", "The adhoc duty ID to cancel (required)")),
                List.of("adhoc_duty_id")));

        tools.add(buildTool("preview_adhoc_personnel",
                "Preview available personnel for adhoc duty assignment. Shows who can be picked based on date, time, and count. Use before create_adhoc_duty to let user see and choose personnel.",
                Map.of("date", prop("string", "Date in yyyy-MM-dd format (required)"),
                        "start_time", prop("string", "Start time in HH:mm format (required)"),
                        "end_time", prop("string", "End time in HH:mm format (required)"),
                        "count", prop("string", "Number of personnel to preview (required)"),
                        "pick_from", prop("string", "ALL_PLATOONS or SPECIFIC_PLATOONS - optional, defaults to ALL_PLATOONS")),
                List.of("date", "start_time", "end_time", "count")));

        tools.add(buildTool("search_adhoc_personnel",
                "Search available personnel by name or badge ID for manual addition to adhoc duty. Filters out those on leave or already assigned. Use when user wants to find specific people for adhoc duty.",
                Map.of("query", prop("string", "Search text - name or badge ID (required)"),
                        "date", prop("string", "Duty date in yyyy-MM-dd format (required)"),
                        "start_time", prop("string", "Start time in HH:mm format (required)"),
                        "end_time", prop("string", "End time in HH:mm format (required)")),
                List.of("query", "date", "start_time", "end_time")));

        return tools;
    }

    /**
     * Helper: Build a single tool definition in OpenAI format.
     */
    private Map<String, Object> buildTool(String name, String description,
                                           Map<String, Map<String, String>> properties,
                                           List<String> required) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", required);

        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        function.put("description", description);
        function.put("parameters", parameters);

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", function);
        return tool;
    }

    /**
     * Helper: Build a property definition.
     */
    private Map<String, String> prop(String type, String description) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("type", type);
        p.put("description", description);
        return p;
    }

    /**
     * Parse the OpenAI-compatible response to extract tool calls or direct responses.
     * Works identically for both Groq and Bedrock since BedrockLlmClient converts to OpenAI format.
     */
    private ActionPlan parseToolCallResponse(String responseBody, String originalInput, String normalizedInput) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return ActionPlan.unknown(originalInput, normalizedInput);
            }

            JsonNode message = choices.get(0).path("message");

            // Check if there's a tool call
            JsonNode toolCalls = message.path("tool_calls");
            if (toolCalls.isArray() && !toolCalls.isEmpty()) {
                JsonNode firstCall = toolCalls.get(0);
                String toolName = firstCall.path("function").path("name").asText();
                String argsJson = firstCall.path("function").path("arguments").asText();

                Map<String, Object> params = new HashMap<>();
                if (argsJson != null && !argsJson.isEmpty()) {
                    JsonNode argsNode = objectMapper.readTree(argsJson);
                    Iterator<Map.Entry<String, JsonNode>> fields = argsNode.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> field = fields.next();
                        params.put(field.getKey(), field.getValue().asText());
                    }
                }

                log.info("Function calling resolved: tool={}, params={}, provider={}",
                        toolName, params, llmClient.getProviderName());
                return ActionPlan.of(toolName, toolName, params, originalInput, normalizedInput);
            }

            // No tool call - LLM wants to respond directly (clarification, greeting, etc.)
            String content = message.path("content").asText("");
            if (!content.isEmpty()) {
                // Strip XML-like response tags that LLMs sometimes wrap their answers in
                content = content.replaceAll("(?i)</?response>", "").trim();
                Map<String, Object> params = new HashMap<>();
                params.put("direct_response", content);
                return ActionPlan.of("direct_response", "direct_response", params, originalInput, normalizedInput);
            }

        } catch (Exception e) {
            log.error("Failed to parse function calling response", e);
        }

        return ActionPlan.unknown(originalInput, normalizedInput);
    }

    /**
     * Try to parse Groq's failed_generation field from a 400 error.
     * Groq's Llama 3.3 sometimes returns tool calls in <function=name{args}</function> format
     * instead of proper tool_calls JSON, causing a 400 error.
     */
    private ActionPlan tryParseFailedGeneration(String body, String userMessage, String normalizedInput) {
        try {
            if (body == null || !body.contains("<function=")) {
                // Try parsing as JSON error response
                JsonNode errorRoot = objectMapper.readTree(body);
                String failedGen = errorRoot.path("error").path("failed_generation").asText("");
                if (failedGen.isBlank() || !failedGen.contains("<function=")) {
                    return null;
                }
                body = failedGen;
            }

            // Parse: <function=search_personnel{"query": "jayanth"}</function>
            String cleaned = body;
            if (cleaned.contains("<function=")) {
                cleaned = cleaned.substring(cleaned.indexOf("<function="));
                cleaned = cleaned.replace("<function=", "").replace("</function>", "").trim();
            }

            int braceIdx = cleaned.indexOf('{');
            if (braceIdx > 0) {
                String toolName = cleaned.substring(0, braceIdx).trim();
                String argsJson = cleaned.substring(braceIdx).trim();
                Map<String, Object> params = new HashMap<>();
                JsonNode argsNode = objectMapper.readTree(argsJson);
                argsNode.fields().forEachRemaining(f -> params.put(f.getKey(), f.getValue().asText()));
                log.info("Parsed failed_generation: tool={}, params={}", toolName, params);
                return ActionPlan.of(toolName, toolName, params, userMessage, normalizedInput);
            }
        } catch (Exception parseEx) {
            log.debug("Failed to parse failed_generation: {}", parseEx.getMessage());
        }
        return null;
    }
}
