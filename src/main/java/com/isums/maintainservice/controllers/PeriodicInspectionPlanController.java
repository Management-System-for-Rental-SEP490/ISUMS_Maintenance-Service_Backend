package com.isums.maintainservice.controllers;

import com.isums.maintainservice.domains.dtos.ApiResponse;
import com.isums.maintainservice.domains.dtos.ApiResponses;
import com.isums.maintainservice.domains.dtos.PlanDTO.CreatePlanRequest;
import com.isums.maintainservice.domains.dtos.PlanDTO.PlanDetailDto;
import com.isums.maintainservice.domains.dtos.PlanDTO.PlanDto;
import com.isums.maintainservice.domains.dtos.PlanHouseDTO.AddHousesRequest;
import com.isums.maintainservice.domains.dtos.PlanHouseDTO.PlanHouseDto;
import com.isums.maintainservice.infrastructures.abstracts.PeriodicInspectionPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/maintenances/plans")
@RequiredArgsConstructor
public class PeriodicInspectionPlanController {
    private final PeriodicInspectionPlanService periodicInspectionPlanService;

    @PostMapping
    public ApiResponse<PlanDto> createPlan(@AuthenticationPrincipal Jwt jwt,@RequestBody CreatePlanRequest request){
        UUID managerId = UUID.fromString(jwt.getSubject());
        PlanDto res = periodicInspectionPlanService.createPlan(managerId,request);
        return ApiResponses.created(res,"Create plan successfully");
    }

    @PostMapping("/houses/{planId}")
    public ApiResponse<List<PlanHouseDto>> addHousesToPlan(@PathVariable UUID planId, @RequestBody AddHousesRequest request){
         List<PlanHouseDto> res = periodicInspectionPlanService.addHousesToPlan(planId,request.houseIds());
         return ApiResponses.ok(res,"Add houses to plan successfully");
    }

    @GetMapping
    public ApiResponse<List<PlanDto>> getAllPlans(){
        List<PlanDto> res = periodicInspectionPlanService.getAllPlans();
        return ApiResponses.ok(res,"Get all plans successfully");
    }

    @GetMapping("/{planId}")
    public ApiResponse<PlanDetailDto> getPlanById(@PathVariable UUID planId){
        PlanDetailDto res = periodicInspectionPlanService.getPlanById(planId);
        return ApiResponses.ok(res,"Get plan information successfully");
    }

}
