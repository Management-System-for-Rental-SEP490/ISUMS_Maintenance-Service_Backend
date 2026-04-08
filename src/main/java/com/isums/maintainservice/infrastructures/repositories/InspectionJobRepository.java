package com.isums.maintainservice.infrastructures.repositories;

import com.isums.maintainservice.domains.entities.InspectionJob;
import com.isums.maintainservice.domains.enums.InspectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InspectionJobRepository extends JpaRepository<InspectionJob, UUID> {
    List<InspectionJob> findByStatus(InspectionStatus status);
}