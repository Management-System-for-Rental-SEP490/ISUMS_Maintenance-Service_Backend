package common.i18n;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class TranslationMap {
    private final Map<String, String> translations;

    public TranslationMap() {
        this.translations = new LinkedHashMap<>();
    }

    public TranslationMap(Map<String, String> translations) {
        this.translations = normalize(translations);
    }

    public static TranslationMap of(Map<String, String> translations) {
        return new TranslationMap(translations);
    }

    public Map<String, String> getTranslations() {
        return translations;
    }

    /** Language resolved from header → JWT → LocaleContext → JVM default. "vi"/"en"/"ja". */
    public static String currentLanguage() {
        return new TranslationMap().resolveLanguage();
    }

    public String resolve() {
        if (translations.isEmpty()) return null;
        String lang = resolveLanguage();
        if (lang == null || lang.isBlank()) lang = Locale.getDefault().getLanguage();
        String direct = translations.get(lang);
        if (direct != null && !direct.isBlank()) return direct;
        String vi = translations.get("vi");
        if (vi != null && !vi.isBlank()) return vi;
        String en = translations.get("en");
        if (en != null && !en.isBlank()) return en;
        return translations.values().stream()
                .filter(v -> v != null && !v.isBlank())
                .findFirst()
                .orElse(null);
    }

    public String resolveFor(String preferredLanguage) {
        if (translations.isEmpty()) return null;
        String lang = normalizeLanguage(preferredLanguage);
        if (lang != null) {
            String direct = translations.get(lang);
            if (direct != null && !direct.isBlank()) return direct;
        }
        return resolve();
    }

    private String resolveLanguage() {
        String headerLanguage = resolveHeaderLanguage();
        if (headerLanguage != null) return headerLanguage;

        String jwtLanguage = resolveJwtLanguage();
        if (jwtLanguage != null) return jwtLanguage;

        String localeLanguage = normalizeLanguage(LocaleContextHolder.getLocale() != null
                ? LocaleContextHolder.getLocale().getLanguage()
                : null);
        if (localeLanguage != null) return localeLanguage;

        return normalizeLanguage(Locale.getDefault().getLanguage());
    }

    private String resolveHeaderLanguage() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        if (request == null) return null;
        String header = request.getHeader("Accept-Language");
        if (header == null || header.isBlank()) return null;
        String candidate = header.split(",")[0].trim();
        if (candidate.isBlank() || candidate.startsWith("*")) return null;
        candidate = candidate.split(";")[0].trim();
        return normalizeLanguage(Locale.forLanguageTag(candidate).getLanguage());
    }

    private String resolveJwtLanguage() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        return normalizeLanguage(jwt.getClaimAsString("locale"));
    }

    private String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return null;
        }
        return language.trim().toLowerCase(Locale.ROOT);
    }

    private static Map<String, String> normalize(Map<String, String> input) {
        Map<String, String> out = new LinkedHashMap<>();
        if (input == null) return out;
        for (Map.Entry<String, String> entry : input.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) continue;
            if (entry.getValue() == null || entry.getValue().isBlank()) continue;
            out.put(entry.getKey().trim().toLowerCase(Locale.ROOT), entry.getValue().trim());
        }
        return out;
    }
}
