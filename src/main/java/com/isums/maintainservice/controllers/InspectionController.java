package com.isums.maintainservice.controllers;

import com.isums.maintainservice.domains.dtos.*;
import com.isums.maintainservice.domains.dtos.MaintainJobDTO.MaintenanceJobDto;
import com.isums.maintainservice.domains.enums.InspectionStatus;
import com.isums.maintainservice.infrastructures.abstracts.InspectionJobService;
import common.paginations.dtos.PageRequestParams;
import common.paginations.dtos.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/maintenances/inspections")
@RequiredArgsConstructor
public class InspectionController {

    private final InspectionJobService inspectionJobService;

    @PostMapping
    public ApiResponse<InspectionDto> createInspection(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CreateInspectionRequest request) {
        InspectionDto res = inspectionJobService.create(jwt.getSubject(), request);
        return ApiResponses.ok(res, "Create inspection successfully");
    }

    @GetMapping("/{id}")
    public ApiResponse<InspectionDto> getInspection(@PathVariable UUID id) {
        InspectionDto res = inspectionJobService.getInspectionById(id);
        return ApiResponses.ok(res, "Get inspection successfully");
    }

    @GetMapping
    public com.isums.maintainservice.domains.dtos.ApiResponse<PageResponse<InspectionDto>> getAll(
            @ParameterObject @Valid @ModelAttribute PageRequestParams params) {
        return com.isums.maintainservice.domains.dtos.ApiResponses.ok(inspectionJobService.getAll(params.toPageRequest()), "Success");
    }

    @PutMapping("/{id}/status")
    public ApiResponse<InspectionDto> updateStatus(@PathVariable UUID id, @RequestBody UpdateInspectionRequest request) {
        InspectionDto res = inspectionJobService.updateStatus(id, request.status());
        return ApiResponses.ok(res, "Update inspection status successfully");
    }

    @PostMapping(value = "/{id}/house-photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<InspectionDto> uploadHousePhotos(
            @PathVariable UUID id,
            @RequestParam("files") List<MultipartFile> files) {
        InspectionDto res = inspectionJobService.uploadHousePhotos(id, files);
        return ApiResponses.ok(res, "House photos uploaded");
    }
}
