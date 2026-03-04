package com.isums.maintainservice.domains.dtos.PlanDTO;

import com.isums.maintainservice.domains.enums.FrequencyType;

import java.time.LocalDate;

public record CreatePlanRequest(
        String name,
        FrequencyType frequencyType,
        Integer frequencyValue,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        LocalDate nextRunAt

) {

}
