package com.policescheduler.controller;

import com.policescheduler.dto.AuthResponse;
import com.policescheduler.dto.ChangePasswordRequest;
import com.policescheduler.dto.ForgotPasswordRequest;
import com.policescheduler.dto.ForgotPasswordResponse;
import com.policescheduler.dto.LoginRequest;
import com.policescheduler.dto.ResetPasswordRequest;
import com.policescheduler.dto.ResetPasswordResponse;
import com.policescheduler.dto.VerifyOtpRequest;
import com.policescheduler.dto.VerifyOtpResponse;
import com.policescheduler.service.AuthService;
import com.policescheduler.service.ForgotPasswordService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final ForgotPasswordService forgotPasswordService;

    public AuthController(AuthService authService, ForgotPasswordService forgotPasswordService) {
        this.authService = authService;
        this.forgotPasswordService = forgotPasswordService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }
        authService.logout(token);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(authentication.getName(), request);
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }

    @PostMapping("/forgot-password/request")
    public ResponseEntity<ForgotPasswordResponse> forgotPasswordRequest(
            @Valid @RequestBody ForgotPasswordRequest request) {
        ForgotPasswordResponse response = forgotPasswordService.requestOtp(request.getEmail());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password/verify-otp")
    public ResponseEntity<VerifyOtpResponse> forgotPasswordVerifyOtp(
            @Valid @RequestBody VerifyOtpRequest request) {
        VerifyOtpResponse response = forgotPasswordService.verifyOtp(request.getEmail(), request.getOtp());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password/reset")
    public ResponseEntity<ResetPasswordResponse> forgotPasswordReset(
            @Valid @RequestBody ResetPasswordRequest request) {
        ResetPasswordResponse response = forgotPasswordService.resetPassword(request.getResetToken(), request.getNewPassword());
        return ResponseEntity.ok(response);
    }
}
