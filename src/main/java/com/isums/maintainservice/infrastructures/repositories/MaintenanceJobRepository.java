package com.isums.maintainservice.infrastructures.repositories;

import com.isums.maintainservice.domains.dtos.MaintainJobDTO.MaintenanceJobDto;
import com.isums.maintainservice.domains.entities.MaintenanceJob;
import com.isums.maintainservice.domains.enums.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface MaintenanceJobRepository extends JpaRepository<MaintenanceJob, UUID> {
    List<MaintenanceJob> findByHouseIdOrderByCreatedAtDesc(UUID houseId);
    List<MaintenanceJob> findByStatus(JobStatus status);
    List<MaintenanceJob> findAllByOrderByCreatedAtDesc();
    boolean existsByPlanIdAndHouseIdAndPeriodStartDate(UUID planId, UUID houseId, LocalDate periodStartDate);
}
