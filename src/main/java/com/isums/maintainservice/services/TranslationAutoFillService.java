package com.isums.maintainservice.services;

import common.i18n.TranslationMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.translate.TranslateClient;
import software.amazon.awssdk.services.translate.model.TranslateTextRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TranslationAutoFillService {
    private static final List<String> TARGETS = List.of("vi", "en", "ja");
    private static final String DEFAULT_SOURCE = "vi";

    private final TranslateClient translateClient;

    public TranslationMap complete(String text, String sourceLanguage) {
        if (text == null || text.isBlank()) {
            return new TranslationMap();
        }
        String source = normalize(sourceLanguage);
        if (!TARGETS.contains(source)) source = DEFAULT_SOURCE;

        Map<String, String> out = new LinkedHashMap<>();
        String trimmed = text.trim();
        out.put(source, trimmed);
        for (String target : TARGETS) {
            if (target.equals(source)) continue;
            out.put(target, translate(trimmed, source, target));
        }
        return TranslationMap.of(out);
    }

    public TranslationMap complete(String text) {
        return complete(text, DEFAULT_SOURCE);
    }

    private String translate(String text, String source, String target) {
        try {
            return translateClient.translateText(TranslateTextRequest.builder()
                    .sourceLanguageCode(source)
                    .targetLanguageCode(target)
                    .text(text)
                    .build()).translatedText();
        } catch (Exception ex) {
            return text;
        }
    }

    private static String normalize(String lang) {
        if (lang == null || lang.isBlank()) return DEFAULT_SOURCE;
        return lang.trim().toLowerCase(Locale.ROOT);
    }
}
