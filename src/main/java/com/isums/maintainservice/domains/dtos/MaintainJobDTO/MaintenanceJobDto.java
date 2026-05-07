package com.isums.maintainservice.domains.dtos.MaintainJobDTO;

import com.isums.maintainservice.domains.enums.JobStatus;
import java.time.LocalDate;
import java.util.UUID;

public record MaintenanceJobDto(
        UUID id,
        UUID planId,
        UUID houseId,
        UUID assignedStaffId,
        String staffName,
        String staffPhone,
        MaintenanceJobStaffDto staff,
        LocalDate periodStartDate,
        JobStatus status
) {
}
