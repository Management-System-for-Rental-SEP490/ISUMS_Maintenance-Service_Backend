package com.isums.maintainservice.controllers;

import com.isums.maintainservice.domains.dtos.*;
import com.isums.maintainservice.domains.enums.InspectionStatus;
import com.isums.maintainservice.infrastructures.abstracts.InspectionJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/maintenances/inspections")
@RequiredArgsConstructor
public class InspectionController {

    private final InspectionJobService inspectionJobService;

    @PostMapping
    public ApiResponse<InspectionDto> createInspection(@RequestBody CreateInspectionRequest request) {
        InspectionDto res = inspectionJobService.create(request);
        return ApiResponses.ok(res, "Create inspection successfully");
    }

    @GetMapping("/{id}")
    public ApiResponse<InspectionDto> getInspection(@PathVariable UUID id) {
        InspectionDto res = inspectionJobService.getInspectionById(id);
        return ApiResponses.ok(res, "Get inspection successfully");
    }

    @GetMapping
    public ApiResponse<List<InspectionDto>> getAll(@RequestParam(required = false) InspectionStatus status) {
        List<InspectionDto> res = inspectionJobService.getAll(status);
        return ApiResponses.ok(res, "Get inspections successfully");
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<InspectionDto> updateStatus(@PathVariable UUID id, @RequestBody UpdateInspectionRequest request) {
        InspectionDto res = inspectionJobService.updateStatus(id, request.status());
        return ApiResponses.ok(res, "Update inspection status successfully");
    }
}
