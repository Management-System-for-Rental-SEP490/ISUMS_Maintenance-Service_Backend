package com.isums.maintainservice.controllers;


import com.isums.maintainservice.domains.dtos.ApiResponse;
import com.isums.maintainservice.domains.dtos.ApiResponses;
import com.isums.maintainservice.domains.dtos.PlanHouseDTO.PlanHouseDto;
import com.isums.maintainservice.infrastructures.abstracts.PeriodicInspectionPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/maintain/planhouses")
@RequiredArgsConstructor
public class PlanHouseController {
    private final PeriodicInspectionPlanService periodicInspectionPlanService;
    @GetMapping
    public ApiResponse<List<PlanHouseDto>> getAllPlanHouses(){
        List<PlanHouseDto> res = periodicInspectionPlanService.getAllPlanHouse();
        return ApiResponses.ok(res,"Get all plans successfully");
    }
    @DeleteMapping("/houses/{planId}/{houseId}")
    public ApiResponse<Boolean> removeHouseFromPlan(@PathVariable UUID planId, @PathVariable UUID houseId
    ){

        Boolean res = periodicInspectionPlanService.removeHouseFromPlan(planId, houseId);

        return ApiResponses.ok(res,"Remove house from plan successfully");
    }
}
