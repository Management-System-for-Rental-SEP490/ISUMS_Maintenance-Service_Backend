package com.isums.maintainservice.controllers;

import com.isums.maintainservice.domains.dtos.ApiResponse;
import com.isums.maintainservice.domains.dtos.ApiResponses;
import com.isums.maintainservice.domains.dtos.MaintenanceExecution.CreateExecutionRequest;
import com.isums.maintainservice.domains.dtos.MaintenanceExecution.ExecutionDto;
import com.isums.maintainservice.infrastructures.abstracts.MaintenanceExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/maintenance/executions")
public class MaintenanceExecutionController {
    private final MaintenanceExecutionService maintenanceExecutionService;

    @PostMapping
    public ApiResponse<ExecutionDto> createExecution(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateExecutionRequest req){
        UUID staffId = UUID.fromString(jwt.getSubject());
        ExecutionDto res = maintenanceExecutionService.createExecution(staffId,req);
        return ApiResponses.created(res,"Create execution successfully");
    }

    @GetMapping
    public ApiResponse<List<ExecutionDto>> getAllExecutions(){
        List<ExecutionDto> res = maintenanceExecutionService.getAllExecutions();
        return ApiResponses.ok(res,"Get all executions successfully");
    }

    @GetMapping("/job/{jobId}")
    public ApiResponse<List<ExecutionDto>> getExecutionsByJob(@PathVariable UUID jobId){

        List<ExecutionDto> res = maintenanceExecutionService.getExecutionsByJobId(jobId);

        return ApiResponses.ok(res,"Get executions successfully");
    }

    @GetMapping("/house/{houseId}")
    public ApiResponse<List<ExecutionDto>> getExecutionsByHouse(@PathVariable UUID houseId){
        List<ExecutionDto> res =
                maintenanceExecutionService.getExecutionsByHouseId(houseId);
        return ApiResponses.ok(res,"Get maintenance history successfully");
    }
}
