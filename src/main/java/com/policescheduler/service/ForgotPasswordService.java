package com.policescheduler.service;

import com.policescheduler.dto.ForgotPasswordResponse;
import com.policescheduler.dto.ResetPasswordResponse;
import com.policescheduler.dto.VerifyOtpResponse;
import com.policescheduler.entity.Personnel;
import com.policescheduler.entity.User;
import com.policescheduler.repository.PersonnelRepository;
import com.policescheduler.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ForgotPasswordService {

    private static final Logger log = LoggerFactory.getLogger(ForgotPasswordService.class);

    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final int RESET_TOKEN_EXPIRY_MINUTES = 10;
    private static final int RATE_LIMIT_MAX_REQUESTS = 5;
    private static final int RATE_LIMIT_WINDOW_MINUTES = 15;

    // --- Inner static classes ---

    static class OtpEntry {
        String otp;
        String email;
        String username;
        Instant createdAt;
        int attempts;

        OtpEntry(String otp, String email, String username, Instant createdAt, int attempts) {
            this.otp = otp;
            this.email = email;
            this.username = username;
            this.createdAt = createdAt;
            this.attempts = attempts;
        }
    }

    static class ResetTokenEntry {
        String username;
        Instant createdAt;

        ResetTokenEntry(String username, Instant createdAt) {
            this.username = username;
            this.createdAt = createdAt;
        }
    }

    static class RateLimitEntry {
        int requestCount;
        Instant windowStart;

        RateLimitEntry(int requestCount, Instant windowStart) {
            this.requestCount = requestCount;
            this.windowStart = windowStart;
        }
    }

    // --- Stores ---

    private final ConcurrentHashMap<String, OtpEntry> otpStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ResetTokenEntry> resetTokenStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RateLimitEntry> rateLimitStore = new ConcurrentHashMap<>();

    // --- Dependencies ---

    private final PersonnelRepository personnelRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailNotificationService emailNotificationService;
    private final SecureRandom secureRandom = new SecureRandom();

    public ForgotPasswordService(PersonnelRepository personnelRepository,
                                 UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,
                                 EmailNotificationService emailNotificationService) {
        this.personnelRepository = personnelRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailNotificationService = emailNotificationService;
    }

    // --- Public methods (stubs — implementation in tasks 3.2–3.5) ---

    public ForgotPasswordResponse requestOtp(String email) {
        // 1. Normalize email to lowercase
        String normalizedEmail = email.toLowerCase().trim();

        // 2. Check rate limit
        checkRateLimit(normalizedEmail);

        // 3. Lookup user by email — first check users table directly, then personnel table
        User user = null;

        // Try 1: Check email directly on users table
        Optional<User> userDirectOpt = userRepository.findFirstByEmailIgnoreCase(normalizedEmail);
        if (userDirectOpt.isPresent()) {
            user = userDirectOpt.get();
            log.debug("User found directly by email in users table");
        } else {
            // Try 2: Check via personnel table linkage
            Optional<Personnel> personnelOpt = personnelRepository.findByEmailIgnoreCase(normalizedEmail);
            if (personnelOpt.isPresent()) {
                Personnel personnel = personnelOpt.get();
                log.debug("Personnel found with id: {} for email lookup", personnel.getId());
                Optional<User> userOpt = userRepository.findByPersonnelId(personnel.getId());
                if (userOpt.isPresent()) {
                    user = userOpt.get();
                    log.debug("User found via personnel linkage");
                }
            }
        }

        // 4. If no user found by either method, return error
        if (user == null) {
            log.info("No account found for email: {} during password reset request", normalizedEmail.replaceAll("(?<=.{3}).(?=.*@)", "*"));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No account found with this email address. Please check and try again.");
        }

        // 5. Generate 6-digit OTP
        String otp = String.format("%06d", secureRandom.nextInt(1_000_000));

        // Store in otpStore (replaces any existing entry for this email)
        otpStore.put(normalizedEmail, new OtpEntry(otp, normalizedEmail, user.getUsername(), Instant.now(), 0));

        // 6. Send OTP email
        try {
            emailNotificationService.sendOtpEmail(normalizedEmail, otp);
            log.info("OTP generated and email sent for password reset request");
        } catch (Exception e) {
            log.error("Failed to send OTP email for password reset: {}", e.getMessage());
            // Remove the OTP entry since email failed
            otpStore.remove(normalizedEmail);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to send email. Please try again later.");
        }

        // 7. Return success — OTP was sent
        return ForgotPasswordResponse.builder()
                .message("OTP has been sent to your email address.")
                .build();
    }

    /**
     * Checks rate limit for the given email. Throws ResponseStatusException with 429 if exceeded.
     */
    private void checkRateLimit(String email) {
        RateLimitEntry entry = rateLimitStore.compute(email, (key, existing) -> {
            Instant now = Instant.now();

            if (existing == null) {
                // First request for this email
                return new RateLimitEntry(1, now);
            }

            // If window has expired (more than 15 minutes), reset the counter
            if (Duration.between(existing.windowStart, now).toMinutes() >= RATE_LIMIT_WINDOW_MINUTES) {
                return new RateLimitEntry(1, now);
            }

            // Within the window — check if limit exceeded
            if (existing.requestCount >= RATE_LIMIT_MAX_REQUESTS) {
                return existing; // Don't increment, will throw below
            }

            // Increment the counter
            existing.requestCount++;
            return existing;
        });

        // Check if rate limit is exceeded (after compute, the entry count reflects current state)
        if (entry.requestCount > RATE_LIMIT_MAX_REQUESTS) {
            log.warn("Rate limit exceeded for password reset request");
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please try again later.");
        }
    }

    public VerifyOtpResponse verifyOtp(String email, String otp) {
        // 1. Normalize email to lowercase
        String normalizedEmail = email.toLowerCase().trim();

        // 2. Lookup OTP entry from otpStore
        OtpEntry entry = otpStore.get(normalizedEmail);

        // 3. If no entry found, throw error
        if (entry == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired OTP");
        }

        // 4. Check expiry
        if (Duration.between(entry.createdAt, Instant.now()).toMinutes() >= OTP_EXPIRY_MINUTES) {
            otpStore.remove(normalizedEmail);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired OTP");
        }

        // 5. Validate OTP match
        if (!entry.otp.equals(otp)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired OTP");
        }

        // 6. On success: remove OTP (single-use), generate reset token, store it
        otpStore.remove(normalizedEmail);
        String resetToken = UUID.randomUUID().toString();
        resetTokenStore.put(resetToken, new ResetTokenEntry(entry.username, Instant.now()));

        return VerifyOtpResponse.builder().resetToken(resetToken).build();
    }

    @Transactional
    public ResetPasswordResponse resetPassword(String resetToken, String newPassword) {
        // 1. Lookup reset token entry
        ResetTokenEntry entry = resetTokenStore.get(resetToken);

        // 2. If not found, throw error
        if (entry == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token");
        }

        // 3. Check token expiry (10 minutes)
        if (Duration.between(entry.createdAt, Instant.now()).toMinutes() >= RESET_TOKEN_EXPIRY_MINUTES) {
            resetTokenStore.remove(resetToken);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token");
        }

        // 4. Lookup user by username
        Optional<User> userOpt = userRepository.findByUsername(entry.username);

        // 5. If user not found, throw error
        if (userOpt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token");
        }

        User user = userOpt.get();

        // 6. Encode new password
        String encodedPassword = passwordEncoder.encode(newPassword);

        // 7. Update user with new password and save
        user.setPasswordHash(encodedPassword);
        userRepository.save(user);

        // 8. Remove reset token to prevent reuse
        resetTokenStore.remove(resetToken);

        // 9. Log success (no sensitive data)
        log.info("Password reset completed successfully for user");

        // 10. Return success response
        return ResetPasswordResponse.builder().message("Password reset successful").build();
    }

    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void cleanupExpiredEntries() {
        Instant now = Instant.now();

        // Purge expired OTP entries (>10 min)
        otpStore.entrySet().removeIf(entry ->
            Duration.between(entry.getValue().createdAt, now).toMinutes() >= OTP_EXPIRY_MINUTES);

        // Purge expired reset tokens (>10 min)
        resetTokenStore.entrySet().removeIf(entry ->
            Duration.between(entry.getValue().createdAt, now).toMinutes() >= RESET_TOKEN_EXPIRY_MINUTES);

        // Purge expired rate limit entries (>15 min)
        rateLimitStore.entrySet().removeIf(entry ->
            Duration.between(entry.getValue().windowStart, now).toMinutes() >= RATE_LIMIT_WINDOW_MINUTES);

        log.debug("Cleanup completed. OTP entries: {}, Reset tokens: {}, Rate limits: {}",
                  otpStore.size(), resetTokenStore.size(), rateLimitStore.size());
    }
}
