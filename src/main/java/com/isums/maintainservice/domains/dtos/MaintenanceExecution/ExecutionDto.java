package com.isums.maintainservice.domains.dtos.MaintenanceExecution;

import java.time.Instant;
import java.util.UUID;

public record ExecutionDto(
        UUID id,
        UUID jobId,
        UUID houseId,
        UUID assetId,
        UUID staffId,
        Integer conditionScore,
        String notes,
        Instant createdAt
){
}
