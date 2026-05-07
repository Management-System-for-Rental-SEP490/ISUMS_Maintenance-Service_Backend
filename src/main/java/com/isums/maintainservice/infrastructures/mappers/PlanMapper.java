package com.isums.maintainservice.infrastructures.mappers;

import com.isums.maintainservice.domains.dtos.PlanDTO.PlanDto;
import com.isums.maintainservice.domains.dtos.PlanHouseDTO.PlanHouseDto;
import com.isums.maintainservice.domains.entities.PeriodicInspectionPlan;
import com.isums.maintainservice.domains.entities.PlanHouse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PlanMapper {
    @Mapping(target = "name", expression = "java(resolveName(plan))")
    PlanDto plan(PeriodicInspectionPlan plan);
    List<PlanDto> plans(List<PeriodicInspectionPlan> plans);

    PlanHouseDto planHouseDto(PlanHouse planHouse);
    List<PlanHouseDto> planHouseDtos(List<PlanHouse> planHouses);

    default String resolveName(PeriodicInspectionPlan plan) {
        if (plan == null) return null;
        if (plan.getNameTranslations() != null) {
            String resolved = plan.getNameTranslations().resolve();
            if (resolved != null && !resolved.isBlank()) return resolved;
        }
        return plan.getName();
    }
}
