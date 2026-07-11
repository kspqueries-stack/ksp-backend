package com.policescheduler.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class EnvironmentConfig {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentConfig.class);

    @Value("${spring.datasource.url:}")
    private String dbUrl;

    @Value("${spring.datasource.username:}")
    private String dbUsername;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    @Value("${app.jwt.secret:}")
    private String jwtSecret;

    @Value("${app.ai-provider:groq}")
    private String aiProvider;

    @Value("${app.groq.api-key:}")
    private String groqApiKey;

    @PostConstruct
    public void validateRequiredEnvironmentVariables() {
        List<String> missing = new ArrayList<>();

        if (isBlank(dbUrl)) {
            missing.add("DB_URL (spring.datasource.url)");
        }
        if (isBlank(dbUsername)) {
            missing.add("DB_USERNAME (spring.datasource.username)");
        }
        if (isBlank(dbPassword)) {
            missing.add("DB_PASSWORD (spring.datasource.password)");
        }
        if (isBlank(jwtSecret)) {
            missing.add("JWT_SECRET (app.jwt.secret)");
        }

        // Only require Groq API key if Groq is the active provider
        if ("groq".equalsIgnoreCase(aiProvider) && isBlank(groqApiKey)) {
            missing.add("GROQ_API_KEY (app.groq.api-key)");
        }

        if (!missing.isEmpty()) {
            String errorMessage = "Application startup failed. The following required environment variables are missing or empty: "
                    + String.join(", ", missing);
            log.error(errorMessage);
            throw new IllegalStateException(errorMessage);
        }

        log.info("All required environment variables configured. AI provider: {}", aiProvider);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public String getDbUrl() {
        return dbUrl;
    }

    public String getDbUsername() {
        return dbUsername;
    }

    public String getDbPassword() {
        return dbPassword;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public String getGroqApiKey() {
        return groqApiKey;
    }

    public String getAiProvider() {
        return aiProvider;
    }
}
