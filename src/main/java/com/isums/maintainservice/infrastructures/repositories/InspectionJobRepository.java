package com.isums.maintainservice.infrastructures.repositories;

import com.isums.maintainservice.domains.entities.InspectionJob;
import com.isums.maintainservice.domains.enums.InspectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InspectionJobRepository extends JpaRepository<InspectionJob, UUID> ,JpaSpecificationExecutor<InspectionJob> {
    List<InspectionJob> findByStatus(InspectionStatus status);

    Optional<InspectionJob> findFirstByContractIdAndStatusInOrderByUpdatedAtDesc(
            UUID contractId, List<InspectionStatus> statuses);
}