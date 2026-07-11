package com.policescheduler.controller;

import com.policescheduler.dto.TranslationEntry;
import com.policescheduler.service.I18nService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/i18n")
public class I18nController {

    private final I18nService i18nService;

    public I18nController(I18nService i18nService) {
        this.i18nService = i18nService;
    }

    @GetMapping("/translations/{locale}")
    public ResponseEntity<Map<String, String>> getTranslations(@PathVariable String locale) {
        return ResponseEntity.ok(i18nService.getTranslations(locale));
    }

    @PostMapping("/translations")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> upsertTranslation(
            @Valid @RequestBody TranslationEntry entry) {
        i18nService.upsertTranslation(entry);
        return ResponseEntity.ok(Map.of("message", "Translation saved successfully"));
    }

    @GetMapping("/missing")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<String>> getMissingTranslations(
            @RequestParam(defaultValue = "kn") String locale) {
        return ResponseEntity.ok(i18nService.getMissingTranslations(locale));
    }
}
