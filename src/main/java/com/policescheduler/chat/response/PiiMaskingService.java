package com.policescheduler.chat.response;

import com.policescheduler.dto.ChatResponse;
import com.policescheduler.dto.chat.TableResponseData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class PiiMaskingService {

    private static final Logger log = LoggerFactory.getLogger(PiiMaskingService.class);

    private static final Pattern PHONE_PATTERN = Pattern.compile("\\b\\d{10,}\\b");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    public ChatResponse maskPii(ChatResponse response, String userRole) {
        if (response == null || "ADMIN".equalsIgnoreCase(userRole)) {
            return response;
        }

        // Mask message text
        String maskedMessage = maskText(response.getResponse());
        response.setResponse(maskedMessage);

        // Mask table data
        if (response.getData() instanceof TableResponseData tableData) {
            List<Map<String, Object>> maskedRows = tableData.getRows().stream()
                    .map(row -> maskRow(row))
                    .collect(Collectors.toList());
            tableData.setRows(maskedRows);
        }

        return response;
    }

    private Map<String, Object> maskRow(Map<String, Object> row) {
        Map<String, Object> masked = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String key = entry.getKey().toLowerCase();
            Object value = entry.getValue();
            if (value instanceof String strValue) {
                if (key.contains("phone") || key.contains("mobile")) {
                    masked.put(entry.getKey(), maskPhone(strValue));
                } else if (key.contains("email")) {
                    masked.put(entry.getKey(), maskEmail(strValue));
                } else {
                    masked.put(entry.getKey(), maskText(strValue));
                }
            } else {
                masked.put(entry.getKey(), value);
            }
        }
        return masked;
    }

    private String maskText(String text) {
        if (text == null) return null;
        String result = EMAIL_PATTERN.matcher(text).replaceAll(match -> maskEmail(match.group()));
        result = PHONE_PATTERN.matcher(result).replaceAll(match -> maskPhone(match.group()));
        return result;
    }

    static String maskPhone(String phone) {
        if (phone == null || phone.equals("—") || phone.length() < 4) return phone;
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() < 4) return phone;
        return digits.substring(0, 2) + "*".repeat(digits.length() - 4) + digits.substring(digits.length() - 2);
    }

    static String maskEmail(String email) {
        if (email == null || email.equals("—") || !email.contains("@")) return email;
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) return email;
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
