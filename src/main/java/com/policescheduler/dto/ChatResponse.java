package com.policescheduler.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private String response;
    private String commandType;
    private String responseType; // "text" | "table" | "form" | "confirmation"
    private Object data;
    private String language; // "en" | "kn"
}
