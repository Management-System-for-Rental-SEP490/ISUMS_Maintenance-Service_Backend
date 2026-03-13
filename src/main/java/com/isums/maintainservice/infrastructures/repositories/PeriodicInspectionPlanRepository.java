package com.isums.maintainservice.infrastructures.repositories;

import com.isums.maintainservice.domains.entities.PeriodicInspectionPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PeriodicInspectionPlanRepository extends JpaRepository<PeriodicInspectionPlan, UUID> {
    List<PeriodicInspectionPlan> findByIsActiveTrue();
    List<PeriodicInspectionPlan> findByNextRunAtLessThanEqualAndIsActiveTrue(LocalDate date);
}
