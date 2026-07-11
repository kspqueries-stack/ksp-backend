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
public class FormSubmitRequest {

    private String formId;
    private String submitAction;
    private Map<String, String> fields;
}
