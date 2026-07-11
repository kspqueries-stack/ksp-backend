package com.policescheduler.service;

import com.policescheduler.dto.TranslationEntry;
import com.policescheduler.entity.Translation;
import com.policescheduler.repository.TranslationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class I18nService {

    private static final Logger log = LoggerFactory.getLogger(I18nService.class);
    private static final String DEFAULT_LOCALE = "en";

    private final TranslationRepository translationRepository;

    public I18nService(TranslationRepository translationRepository) {
        this.translationRepository = translationRepository;
    }

    /**
     * Get all translations for a given locale as a key-value map.
     */
    public Map<String, String> getTranslations(String locale) {
        List<Translation> translations = translationRepository.findByLocale(locale);
        return translations.stream()
                .collect(Collectors.toMap(
                        Translation::getTranslationKey,
                        Translation::getTranslationValue,
                        (existing, replacement) -> existing
                ));
    }

    /**
     * Add or update a translation entry.
     */
    @Transactional
    public void upsertTranslation(TranslationEntry entry) {
        Optional<Translation> existing = translationRepository
                .findByTranslationKeyAndLocale(entry.getTranslationKey(), entry.getLocale());

        if (existing.isPresent()) {
            Translation translation = existing.get();
            translation.setTranslationValue(entry.getTranslationValue());
            if (entry.getCategory() != null) {
                translation.setCategory(entry.getCategory());
            }
            translationRepository.save(translation);
        } else {
            Translation translation = new Translation();
            translation.setTranslationKey(entry.getTranslationKey());
            translation.setLocale(entry.getLocale());
            translation.setTranslationValue(entry.getTranslationValue());
            translation.setCategory(entry.getCategory());
            translationRepository.save(translation);
        }
    }

    /**
     * Find translation keys that exist in English but not in the given locale.
     */
    public List<String> getMissingTranslations(String locale) {
        List<Translation> englishTranslations = translationRepository.findByLocale(DEFAULT_LOCALE);
        List<Translation> localeTranslations = translationRepository.findByLocale(locale);

        var localeKeys = localeTranslations.stream()
                .map(Translation::getTranslationKey)
                .collect(Collectors.toSet());

        return englishTranslations.stream()
                .map(Translation::getTranslationKey)
                .filter(key -> !localeKeys.contains(key))
                .collect(Collectors.toList());
    }

    /**
     * Get a single translation with English fallback. Logs missing key.
     */
    public String translate(String key, String locale) {
        Optional<Translation> translation = translationRepository
                .findByTranslationKeyAndLocale(key, locale);

        if (translation.isPresent()) {
            return translation.get().getTranslationValue();
        }

        // Fallback to English
        if (!DEFAULT_LOCALE.equals(locale)) {
            log.warn("Missing translation for key '{}' in locale '{}', falling back to English", key, locale);
            Optional<Translation> fallback = translationRepository
                    .findByTranslationKeyAndLocale(key, DEFAULT_LOCALE);
            if (fallback.isPresent()) {
                return fallback.get().getTranslationValue();
            }
        }

        log.warn("Translation key '{}' not found in any locale", key);
        return key;
    }
}
