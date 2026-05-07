package com.isums.maintainservice.services;

import com.isums.maintainservice.domains.dtos.PlanDTO.CreatePlanRequest;
import com.isums.maintainservice.domains.dtos.PlanDTO.PlanDetailDto;
import com.isums.maintainservice.domains.dtos.PlanDTO.PlanDto;
import com.isums.maintainservice.domains.dtos.PlanHouseDTO.PlanHouseDto;
import com.isums.maintainservice.domains.entities.PeriodicInspectionPlan;
import com.isums.maintainservice.domains.entities.PlanHouse;
import com.isums.maintainservice.infrastructures.abstracts.PeriodicInspectionPlanService;
import com.isums.maintainservice.infrastructures.gRpc.UserClientsGrpc;
import com.isums.maintainservice.infrastructures.i18n.MaintenanceMessageKeys;
import com.isums.maintainservice.infrastructures.mappers.PlanMapper;
import com.isums.maintainservice.infrastructures.repositories.PeriodicInspectionPlanRepository;
import com.isums.maintainservice.infrastructures.repositories.PlanHouseRepository;
import com.isums.maintainservice.exceptions.NotFoundException;
import common.i18n.TranslationMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PeriodicInspectionPlanServiceImpl implements PeriodicInspectionPlanService {
    private static final String DEFAULT_LANGUAGE = "vi";

    private final PeriodicInspectionPlanRepository periodicInspectionPlanRepository;
    private final PlanMapper planMapper;
    private final PlanHouseRepository planHouseRepository;
    private final UserClientsGrpc userClientsGrpc;
    private final TranslationAutoFillService translationAutoFillService;


    @Override
    public PlanDto createPlan(String managerId, CreatePlanRequest req) {
        try{
                String sourceLanguage = resolveUserLanguage(managerId);
                TranslationMap nameTranslations = translationAutoFillService.complete(req.name(), sourceLanguage);

                PeriodicInspectionPlan plan = PeriodicInspectionPlan.builder()
                        .managerId(UUID.fromString(managerId))
                        .name(req.name())
                        .nameTranslations(nameTranslations)
                        .sourceLanguage(sourceLanguage)
                        .frequencyType(req.frequencyType())
                        .frequencyValue(req.frequencyValue())
                        .effectiveFrom(req.effectiveFrom())
                        .effectiveTo(req.effectiveTo())
                        .nextRunAt(req.nextRunAt())
                        .isActive(true)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();

                PeriodicInspectionPlan created = periodicInspectionPlanRepository.save(plan);

                return planMapper.plan(created);
        } catch (Exception ex) {
            throw new RuntimeException(MaintenanceMessageKeys.CANNOT_CREATE_PLAN + ": " + ex.getMessage());
        }
    }

    @Override
    public List<PlanDto> getAllPlans() {
        try{
            List<PeriodicInspectionPlan> plans = periodicInspectionPlanRepository.findAll();
            return planMapper.plans(plans);
        } catch (Exception ex) {
            throw new RuntimeException(MaintenanceMessageKeys.CANNOT_GET_PLANS + ": " + ex.getMessage());
        }
    }

    @Override
    public List<PlanHouseDto> addHousesToPlan(UUID planId, List<UUID> houseIds) {
        try{
            if (!periodicInspectionPlanRepository.existsById(planId)) {
                throw new NotFoundException(MaintenanceMessageKeys.PLAN_NOT_FOUND);
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

        } catch (NotFoundException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException(MaintenanceMessageKeys.CANNOT_ADD_HOUSES_TO_PLAN + ": " + ex.getMessage());
        }
    }

    @Override
    public List<PlanHouseDto> getAllPlanHouse() {
        try{
            List<PlanHouse> planHouses = planHouseRepository.findAll();
            return planMapper.planHouseDtos(planHouses);
        } catch (Exception ex) {
            throw new RuntimeException(MaintenanceMessageKeys.CANNOT_GET_PLAN_HOUSES + ": " + ex.getMessage());
        }
    }

    @Override
    public PlanDetailDto getPlanById(UUID planId) {
        try{
            PeriodicInspectionPlan plan = periodicInspectionPlanRepository.findById(planId)
                    .orElseThrow(() -> new NotFoundException(MaintenanceMessageKeys.PLAN_NOT_FOUND));
            List<PlanHouse> planHouses = planHouseRepository.findByPlanId(planId);

            List<UUID> houseIds = planHouses.stream()
                    .map(PlanHouse::getHouseId)
                    .toList();

            return new PlanDetailDto(
                    plan.getId(),
                    resolveName(plan),
                    plan.getFrequencyType(),
                    plan.getFrequencyValue(),
                    plan.getEffectiveFrom(),
                    plan.getEffectiveTo(),
                    plan.getNextRunAt(),
                    houseIds
            );

        } catch (NotFoundException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException(MaintenanceMessageKeys.CANNOT_GET_PLAN_INFO + ": " + ex.getMessage());
        }
    }

    @Override
    public Boolean removeHouseFromPlan(UUID planId, UUID houseId) {
        PlanHouse planHouse = planHouseRepository.findByPlanIdAndHouseId(planId, houseId)
                    .orElseThrow(() -> new NotFoundException(MaintenanceMessageKeys.HOUSE_NOT_IN_PLAN));

        planHouseRepository.delete(planHouse);

        return true;
    }

    private String resolveUserLanguage(String userId) {
        try {
            String lang = userClientsGrpc.getUser(userId).getLanguage();
            if (lang == null || lang.isBlank()) return DEFAULT_LANGUAGE;
            return lang.trim().toLowerCase(Locale.ROOT);
        } catch (Exception ex) {
            return DEFAULT_LANGUAGE;
        }
    }

    private static String resolveName(PeriodicInspectionPlan plan) {
        if (plan.getNameTranslations() != null) {
            String resolved = plan.getNameTranslations().resolve();
            if (resolved != null && !resolved.isBlank()) return resolved;
        }
        return plan.getName();
    }
}
