package com.isums.maintainservice.infrastructures.repositories;

import com.isums.maintainservice.domains.entities.MaintenanceJobHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MaintenanceJobHistoryRepository extends JpaRepository<MaintenanceJobHistory, UUID> {
}
