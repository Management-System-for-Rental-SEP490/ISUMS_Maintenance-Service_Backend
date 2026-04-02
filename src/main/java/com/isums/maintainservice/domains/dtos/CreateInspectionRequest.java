package com.isums.maintainservice.domains.dtos;

import java.util.UUID;

public record CreateInspectionRequest(
        UUID houseId,
        String note
) {
}
