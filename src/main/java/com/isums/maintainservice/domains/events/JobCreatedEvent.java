package com.isums.maintainservice.domains.events;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.NONE)
public class JobCreatedEvent {
    @JsonProperty("referenceId")
    private UUID referenceId;

    @JsonProperty("houseId")
    private UUID houseId;

    @JsonProperty("referenceType")
    private String referenceType;

    @JsonProperty("type")
    private String type;

    @JsonProperty("messageId")
    private String messageId;

    @JsonCreator
    public static JobCreatedEvent fromJson(
            @JsonProperty("referenceId") UUID referenceId,
            @JsonProperty("houseId") UUID houseId,
            @JsonProperty("referenceType") String referenceType,
            @JsonProperty("type") String type,
            @JsonProperty("messageId") String messageId) {
        return JobCreatedEvent.builder()
                .referenceId(referenceId)
                .houseId(houseId)
                .referenceType(referenceType)
                .type(type)
                .messageId(messageId)
                .build();
    }
}
