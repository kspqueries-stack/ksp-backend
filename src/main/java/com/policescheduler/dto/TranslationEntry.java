package com.policescheduler.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranslationEntry {

    @NotBlank(message = "Translation key is required")
    private String translationKey;

    @NotBlank(message = "Locale is required")
    private String locale;

    @NotBlank(message = "Translation value is required")
    private String translationValue;

    private String category;
}
