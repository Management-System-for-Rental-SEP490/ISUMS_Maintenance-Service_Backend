package com.isums.maintainservice.domains.dtos.PlanHouseDTO;

import java.util.List;
import java.util.UUID;

public record AddHousesRequest(
        List<UUID> houseIds
) {
}
