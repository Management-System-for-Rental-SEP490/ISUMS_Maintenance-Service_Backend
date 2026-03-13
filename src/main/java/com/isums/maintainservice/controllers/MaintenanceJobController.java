package com.isums.maintainservice.controllers;

import com.isums.maintainservice.domains.dtos.ApiResponse;
import com.isums.maintainservice.domains.dtos.ApiResponses;
import com.isums.maintainservice.domains.dtos.MaintainJobDTO.MaintenanceJobDto;
import com.isums.maintainservice.domains.entities.MaintenanceJob;
import com.isums.maintainservice.domains.enums.JobStatus;
import com.isums.maintainservice.infrastructures.abstracts.MaintenanceJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/maintenance/jobs")
@RequiredArgsConstructor
public class MaintenanceJobController {
    private final MaintenanceJobService maintenanceJobService;

    @PostMapping("/generate")
    public ApiResponse<List<MaintenanceJobDto>> generateJobs(){
        List<MaintenanceJobDto> res = maintenanceJobService.generateMaintainJobs();
        return ApiResponses.created(res,"Generate jobs successfully");
    }

    @GetMapping
    public ApiResponse<List<MaintenanceJobDto>> getAllJobs(){
        List<MaintenanceJobDto> res = maintenanceJobService.getAllJobs();
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

    @GetMapping("/status")
    public List<MaintenanceJobDto> getJobsByStatus(@RequestParam JobStatus status) {
        return maintenanceJobService.getJobsByStatus(status);

    }

}
