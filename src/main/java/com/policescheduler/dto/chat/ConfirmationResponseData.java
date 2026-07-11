package com.policescheduler.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmationResponseData {

    private boolean success;
    private String message;
    private Map<String, String> details;
}
