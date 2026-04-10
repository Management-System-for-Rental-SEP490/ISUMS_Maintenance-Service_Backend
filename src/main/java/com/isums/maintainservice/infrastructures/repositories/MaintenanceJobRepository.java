package com.isums.maintainservice.infrastructures.repositories;

import com.isums.maintainservice.domains.dtos.MaintainJobDTO.MaintenanceJobDto;
import com.isums.maintainservice.domains.entities.MaintenanceJob;
import com.isums.maintainservice.domains.enums.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface MaintenanceJobRepository extends JpaRepository<MaintenanceJob, UUID>, JpaSpecificationExecutor<MaintenanceJob> {
    List<MaintenanceJob> findByHouseIdOrderByCreatedAtDesc(UUID houseId);
    List<MaintenanceJob> findByPlanId(UUID planId);
    boolean existsByPlanIdAndHouseIdAndPeriodStartDate(UUID planId, UUID houseId, LocalDate periodStartDate);
    List<MaintenanceJob> findByAssignedStaffIdOrderByCreatedAtDesc(UUID staffId);
    @Query("""
    SELECT m.houseId 
    FROM MaintenanceJob m
    WHERE m.planId = :planId
      AND m.periodStartDate = :periodStart
      AND m.houseId IN :houseIds
    """)
    List<UUID> findExistingHouseIds(UUID planId, LocalDate periodStart, List<UUID> houseIds);
}
