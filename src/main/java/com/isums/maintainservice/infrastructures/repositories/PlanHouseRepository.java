package com.isums.maintainservice.infrastructures.repositories;

import com.isums.maintainservice.domains.entities.PlanHouse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanHouseRepository extends JpaRepository<PlanHouse, UUID> {
    List<PlanHouse> findByPlanId(UUID planId);
    boolean existsByPlanIdAndHouseId(UUID planId, UUID houseId);
    Optional<PlanHouse> findByPlanIdAndHouseId(UUID planId, UUID houseId);
}
