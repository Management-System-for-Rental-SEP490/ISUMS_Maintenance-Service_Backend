package com.isums.maintainservice.controllers;

import com.isums.maintainservice.domains.dtos.ApiResponse;
import com.isums.maintainservice.domains.dtos.ApiResponses;
import com.isums.maintainservice.domains.dtos.MaintainJobDTO.MaintenanceJobDto;
import com.isums.maintainservice.domains.entities.MaintenanceJobHistory;
import com.isums.maintainservice.domains.enums.JobStatus;
import com.isums.maintainservice.infrastructures.abstracts.MaintenanceJobHistoryService;
import com.isums.maintainservice.infrastructures.abstracts.MaintenanceJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/maintenances/jobs")
@RequiredArgsConstructor
public class MaintenanceJobController {
    private final MaintenanceJobService maintenanceJobService;
    private final MaintenanceJobHistoryService maintenanceJobHistoryService;

    @PostMapping("/generate")
    public ApiResponse<List<MaintenanceJobDto>> generateJobs(){
        List<MaintenanceJobDto> res = maintenanceJobService.generateMaintainJobs();
        return ApiResponses.created(res,"Generate jobs successfully");
    }

    @GetMapping
    public ApiResponse<List<MaintenanceJobDto>> getAllJobs(@RequestParam(required = false) JobStatus status){
        List<MaintenanceJobDto> res = maintenanceJobService.getAllJobs(status);
        return ApiResponses.ok(res,"Get all jobs successfully");
    }

    @GetMapping("/house/{houseId}")
    public ApiResponse<List<MaintenanceJobDto>> getJobByHouseId(@PathVariable UUID houseId){
        List<MaintenanceJobDto> res = maintenanceJobService.getJobByHouseId(houseId);
        return ApiResponses.ok(res,"Get job by house successfully");
    }

    @GetMapping("/{jobId}")
    public ApiResponse<MaintenanceJobDto> getJobbyId(@PathVariable UUID jobId){
        MaintenanceJobDto res = maintenanceJobService.getJobById(jobId);
        return ApiResponses.ok(res,"Get job by id successfully");
    }

//    @GetMapping("/status")
//    public List<MaintenanceJobDto> getJobsByStatus(@RequestParam JobStatus status) {
//        return maintenanceJobService.getJobsByStatus(status);
//
//    }

    @GetMapping("/me")
    public ApiResponse<List<MaintenanceJobDto>> getMyJobs(@AuthenticationPrincipal Jwt jwt){
        List<MaintenanceJobDto> res = maintenanceJobService.getJobsByStaffId(jwt.getSubject());
        return ApiResponses.ok(res,"Get my jobs successfully");
    }


    @GetMapping("/plan/{planId}")
    public ApiResponse<List<MaintenanceJobDto>> getMyJobs(@PathVariable UUID planId){
        List<MaintenanceJobDto> res = maintenanceJobService.getJobsByPlanID(planId);
        return ApiResponses.ok(res,"Get my jobs successfully");
    }

    @PutMapping("/{jobId}/status")
    public ApiResponse<MaintenanceJobDto> updateJobStatus(@PathVariable UUID jobId, @RequestParam JobStatus status){
        MaintenanceJobDto res = maintenanceJobService.updateJobStatus(jobId,status);
        return ApiResponses.ok(res,"Update job status successfully");
    }

    @GetMapping("/{jobId}/history")
    public ApiResponse<List<MaintenanceJobHistory>> getJobHistory(@PathVariable UUID jobId){
        List<MaintenanceJobHistory> res = maintenanceJobHistoryService.getJobHistory(jobId);
        return ApiResponses.ok(res,"Get job history successfully");

    }
}
