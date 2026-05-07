package common.i18n;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

@Converter
public class TranslationMapConverter implements AttributeConverter<TranslationMap, String> {
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(TranslationMap attribute) {
        if (attribute == null || attribute.getTranslations().isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(attribute.getTranslations());
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public TranslationMap convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) return new TranslationMap();
        try {
            return TranslationMap.of(MAPPER.readValue(dbData, MAP_TYPE));
        } catch (Exception e) {
            return new TranslationMap();
        }
    }
}
