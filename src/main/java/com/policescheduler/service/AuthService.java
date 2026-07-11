package com.policescheduler.service;

import com.policescheduler.dto.AuthResponse;
import com.policescheduler.dto.ChangePasswordRequest;
import com.policescheduler.dto.LoginRequest;
import com.policescheduler.entity.User;
import com.policescheduler.repository.UserRepository;
import com.policescheduler.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public AuthResponse login(LoginRequest request) {
        log.debug("Login attempt for username: {}", request.getUsername());
        
        User user = userRepository.findByUsername(request.getUsername())
                .orElse(null);

        if (user == null) {
            log.debug("User not found: {}", request.getUsername());
            throw new IllegalArgumentException("Invalid credentials");
        }
        
        log.debug("User found: {}, role: {}, hash length: {}", user.getUsername(), user.getRole(), 
                   user.getPasswordHash() != null ? user.getPasswordHash().length() : 0);
        
        boolean matches = passwordEncoder.matches(request.getPassword(), user.getPasswordHash());
        log.debug("Password match result: {}", matches);
        
        if (!matches) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        String token = jwtUtil.generateToken(user.getUsername(), user.getRole().name());

        return AuthResponse.builder()
                .token(token)
                .role(user.getRole().name())
                .displayName(user.getDisplayName())
                .locale(user.getLocale())
                .build();
    }

    public void logout(String token) {
        log.info("Logout requested. Token should be discarded by the client.");
    }

    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }
}
