package com.isums.maintainservice.infrastructures.repositories;

import com.isums.maintainservice.domains.entities.MaintenanceExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MaintenanceExecutionRepository extends JpaRepository<MaintenanceExecution, UUID> {
    List<MaintenanceExecution> findByJobId(UUID jobId);
    List<MaintenanceExecution> findByHouseIdOrderByCreatedAtDesc(UUID houseId);
}
