package com.isums.maintainservice.domains.dtos;

import com.isums.maintainservice.domains.enums.InspectionStatus;

import java.time.Instant;
import java.util.UUID;

public record InspectionDto(
        UUID id,
        UUID houseId,
        UUID assignedStaffId,
        String staffName,
        String staffPhone,
        UUID slotId,
        InspectionStatus status,
        String note,
        Instant createdAt,
        Instant updatedAt
) {
}
