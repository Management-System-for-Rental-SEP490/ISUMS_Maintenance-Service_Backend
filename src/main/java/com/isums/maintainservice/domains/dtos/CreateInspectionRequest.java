package com.isums.maintainservice.domains.dtos;

import com.isums.maintainservice.domains.enums.InspectionType;

import java.util.UUID;

public record CreateInspectionRequest(
        UUID houseId,
        InspectionType type,
        String note
) {
}
