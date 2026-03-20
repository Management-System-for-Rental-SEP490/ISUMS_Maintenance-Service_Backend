package com.isums.maintainservice.domains.dtos.PlanDTO;

import com.isums.maintainservice.domains.enums.FrequencyType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PlanDto(
        UUID id,
        UUID managerId,
        String name,
        FrequencyType frequencyType,
        Integer frequencyValue,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        LocalDate nextRunAt,
        Boolean isActive,
        Instant createdAt,
        Instant updatedAt
){
}
