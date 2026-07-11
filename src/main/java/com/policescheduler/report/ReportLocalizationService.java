package com.policescheduler.report;

import com.lowagie.text.Font;
import com.policescheduler.entity.Translation;
import com.policescheduler.repository.TranslationRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ReportLocalizationService {

    private final TranslationRepository translationRepository;
    private final PdfStyleHelper pdfStyleHelper;

    public ReportLocalizationService(TranslationRepository translationRepository,
                                     PdfStyleHelper pdfStyleHelper) {
        this.translationRepository = translationRepository;
        this.pdfStyleHelper = pdfStyleHelper;
    }

    /**
     * Resolves a label key to the localized string.
     * Looks up translations table with category 'report' or 'chat_label' and the given locale.
     * Falls back to English if the requested locale translation is missing,
     * then falls back to the key itself.
     */
    public String getLabel(String key, String locale) {
        Optional<Translation> translation = translationRepository
                .findByTranslationKeyAndLocale(key, locale);
        if (translation.isPresent() && isReportCategory(translation.get())) {
            return translation.get().getTranslationValue();
        }

        if (!"en".equals(locale)) {
            Optional<Translation> englishFallback = translationRepository
                    .findByTranslationKeyAndLocale(key, "en");
            if (englishFallback.isPresent() && isReportCategory(englishFallback.get())) {
                return englishFallback.get().getTranslationValue();
            }
        }

        return key;
    }

    /**
     * Resolves a list of column header keys to localized strings.
     */
    public List<String> getColumnHeaders(List<String> keys, String locale) {
        return keys.stream()
                .map(key -> getLabel(key, locale))
                .collect(Collectors.toList());
    }

    /**
     * Returns the appropriate Font for the locale.
     * Kannada locale gets a Kannada-capable font; English gets the normal cell font.
     */
    public Font getFontForLocale(String locale) {
        if ("kn".equals(locale)) {
            return pdfStyleHelper.getKannadaFont();
        }
        return pdfStyleHelper.getCellFont();
    }

    private boolean isReportCategory(Translation translation) {
        String category = translation.getCategory();
        return category != null
                && ("report".equals(category) || "chat_label".equals(category));
    }
}
