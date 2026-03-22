package com.isums.maintainservice.infrastructures.abstracts;

import com.isums.maintainservice.domains.dtos.PlanDTO.CreatePlanRequest;
import com.isums.maintainservice.domains.dtos.PlanDTO.PlanDetailDto;
import com.isums.maintainservice.domains.dtos.PlanDTO.PlanDto;
import com.isums.maintainservice.domains.dtos.PlanHouseDTO.PlanHouseDto;

import java.util.List;
import java.util.UUID;

public interface PeriodicInspectionPlanService {
    PlanDto createPlan(String managerId,CreatePlanRequest req);
    List<PlanDto> getAllPlans();
    List<PlanHouseDto> addHousesToPlan(UUID planId, List<UUID> houseIds);
    List<PlanHouseDto> getAllPlanHouse();
    PlanDetailDto getPlanById(UUID planId);
    Boolean removeHouseFromPlan(UUID planId,UUID houseId);
}
