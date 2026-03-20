package com.isums.maintainservice.domains.dtos.PlanHouseDTO;

import java.time.Instant;
import java.util.UUID;

public record PlanHouseDto(
         UUID id,
         UUID planId,
         UUID houseId,
         Instant createdAt
) {

}
