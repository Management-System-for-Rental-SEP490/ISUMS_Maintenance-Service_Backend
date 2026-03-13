package com.isums.maintainservice.domains.dtos.PlanDTO;

import com.isums.maintainservice.domains.enums.FrequencyType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PlanDetailDto(
        UUID id,
        String name,
        FrequencyType frequencyType,
        Integer frequencyValue,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        LocalDate nextRunAt,
        List<UUID> houseIds
) {
}
