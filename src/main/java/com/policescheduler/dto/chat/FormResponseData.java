package com.policescheduler.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormResponseData {

    private String formId;
    private String title;
    private List<FieldDef> fields;
    private String submitAction;
    private String submitLabel;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldDef {
        private String name;
        private String label;
        private String type;       // "text", "select"
        private boolean required;
        private String value;      // pre-filled value
        private List<String> options; // for select type
    }
}
