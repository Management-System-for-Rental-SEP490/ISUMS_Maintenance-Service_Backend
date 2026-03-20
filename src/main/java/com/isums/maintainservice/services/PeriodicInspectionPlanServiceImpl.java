package com.isums.maintainservice.services;

import com.isums.maintainservice.domains.dtos.PlanDTO.CreatePlanRequest;
import com.isums.maintainservice.domains.dtos.PlanDTO.PlanDetailDto;
import com.isums.maintainservice.domains.dtos.PlanDTO.PlanDto;
import com.isums.maintainservice.domains.dtos.PlanHouseDTO.PlanHouseDto;
import com.isums.maintainservice.domains.entities.PeriodicInspectionPlan;
import com.isums.maintainservice.domains.entities.PlanHouse;
import com.isums.maintainservice.infrastructures.abstracts.PeriodicInspectionPlanService;
import com.isums.maintainservice.infrastructures.mappers.PlanMapper;
import com.isums.maintainservice.infrastructures.repositories.PeriodicInspectionPlanRepository;
import com.isums.maintainservice.infrastructures.repositories.PlanHouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PeriodicInspectionPlanServiceImpl implements PeriodicInspectionPlanService {
    private final PeriodicInspectionPlanRepository periodicInspectionPlanRepository;
    private final PlanMapper planMapper;
    private final PlanHouseRepository planHouseRepository;


    @Override
    public PlanDto createPlan(UUID managerId, CreatePlanRequest req) {
        try{
                PeriodicInspectionPlan plan = PeriodicInspectionPlan.builder()
                        .managerId(managerId)
                        .name(req.name())
                        .frequencyType(req.frequencyType())
                        .frequencyValue(req.frequencyValue())
                        .effectiveFrom(req.effectiveFrom())
                        .effectiveTo(req.effectiveTo())
                        .nextRunAt(req.nextRunAt())
                        .isActive(true)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();

                PeriodicInspectionPlan created =periodicInspectionPlanRepository.save(plan);

                return planMapper.plan(created);
        } catch (Exception ex) {
            throw new RuntimeException("Can't create plan" + ex.getMessage());
        }
    }

    @Override
    public List<PlanDto> getAllPlans() {
        try{
            List<PeriodicInspectionPlan> plans = periodicInspectionPlanRepository.findAll();
            return planMapper.plans(plans);
        } catch (Exception ex) {
            throw new RuntimeException("Can't get all plans" + ex.getMessage());
        }
    }

    @Override
    public List<PlanHouseDto> addHousesToPlan(UUID planId, List<UUID> houseIds) {
        try{
            if (!periodicInspectionPlanRepository.existsById(planId)) {
                throw new RuntimeException("Plan not found");
            }

            Set<UUID> uniqueHouseIds = new HashSet<>(houseIds);

            List<PlanHouse> existingPlanHouses = planHouseRepository.findByPlanId(planId);
            Set<UUID> existingHouseIds = new HashSet<>();

            for(PlanHouse planHouse : existingPlanHouses){
                existingHouseIds.add(planHouse.getHouseId());
            }

            List<PlanHouse> planHouses = new ArrayList<>();

            for(UUID houseId : uniqueHouseIds){

                if (existingHouseIds.contains(houseId)) {
                    continue;
                }

                PlanHouse planHouse = PlanHouse.builder()
                        .planId(planId)
                        .houseId(houseId)
                        .createdAt(Instant.now())
                        .build();
                planHouses.add(planHouse);
            }
            planHouseRepository.saveAll(planHouses);

            return planMapper.planHouseDtos(planHouses);

        } catch (Exception ex) {
            throw new RuntimeException("Can't not add plan to house" + ex.getMessage());
        }
    }

    @Override
    public List<PlanHouseDto> getAllPlanHouse() {
        try{
            List<PlanHouse> planHouses = planHouseRepository.findAll();
            return planMapper.planHouseDtos(planHouses);
        } catch (Exception ex) {
            throw new RuntimeException("Can't get all plan house" + ex.getMessage());
        }
    }

    @Override
    public PlanDetailDto getPlanById(UUID planId) {
        try{
            PeriodicInspectionPlan plan = periodicInspectionPlanRepository.findById(planId)
                    .orElseThrow(() -> new RuntimeException("Plan not found"));
            List<PlanHouse> planHouses = planHouseRepository.findByPlanId(planId);

            List<UUID> houseIds = planHouses.stream()
                    .map(PlanHouse::getHouseId)
                    .toList();

            return new PlanDetailDto(
                    plan.getId(),
                    plan.getName(),
                    plan.getFrequencyType(),
                    plan.getFrequencyValue(),
                    plan.getEffectiveFrom(),
                    plan.getEffectiveTo(),
                    plan.getNextRunAt(),
                    houseIds
            );

        } catch (Exception ex) {
            throw new RuntimeException("Can't get plan information" + ex.getMessage());
        }
    }

    @Override
    public Boolean removeHouseFromPlan(UUID planId, UUID houseId) {
        try{
            PlanHouse planHouse = planHouseRepository.findByPlanIdAndHouseId(planId,houseId)
                    .orElseThrow(()-> new RuntimeException("House not found in plan"));

            planHouseRepository.delete(planHouse);

            return true;
        } catch (Exception ex) {
            throw new RuntimeException("Can't delete house from plan" + ex.getMessage());
        }
    }
}
