package com.policescheduler.controller;

import com.policescheduler.chat.gateway.InputGatewayService;
import com.policescheduler.dto.ChatRequest;
import com.policescheduler.dto.ChatResponse;
import com.policescheduler.dto.chat.FormSubmitRequest;
import com.policescheduler.entity.User;
import com.policescheduler.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final InputGatewayService inputGatewayService;
    private final UserRepository userRepository;

    public ChatController(InputGatewayService inputGatewayService, UserRepository userRepository) {
        this.inputGatewayService = inputGatewayService;
        this.userRepository = userRepository;
    }

    @PostMapping("/message")
    public ResponseEntity<ChatResponse> processMessage(
            @Valid @RequestBody ChatRequest request,
            Authentication authentication) {
        User user = resolveUser(authentication);
        return ResponseEntity.ok(
                inputGatewayService.processTextMessage(user.getId(), user.getRole().name(), request.getMessage()));
    }

    @PostMapping("/voice")
    public ResponseEntity<ChatResponse> processVoice(
            @RequestParam("file") MultipartFile audioFile,
            Authentication authentication) {
        User user = resolveUser(authentication);
        return ResponseEntity.ok(
                inputGatewayService.processVoiceMessage(user.getId(), user.getRole().name(), audioFile));
    }

    @PostMapping("/upload-pdf")
    public ResponseEntity<ChatResponse> uploadPdf(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        User user = resolveUser(authentication);
        return ResponseEntity.ok(inputGatewayService.processDocumentUpload(user.getId(), file));
    }

    @PostMapping("/form-submit")
    public ResponseEntity<ChatResponse> submitForm(
            @RequestBody FormSubmitRequest request,
            Authentication authentication) {
        User user = resolveUser(authentication);
        return ResponseEntity.ok(
                inputGatewayService.processFormSubmission(user.getId(), user.getRole().name(), request));
    }

    private User resolveUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
