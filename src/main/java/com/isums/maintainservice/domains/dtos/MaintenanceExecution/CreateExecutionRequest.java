package com.isums.maintainservice.domains.dtos.MaintenanceExecution;

import java.util.UUID;

public record CreateExecutionRequest(
        UUID jobId,
        UUID houseId,
        UUID assetId,
        Integer conditionScore,
        String notes
) {
}
