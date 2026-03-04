package com.isums.maintainservice.infrastructures.mappers;

import com.isums.maintainservice.domains.dtos.PlanDTO.PlanDto;
import com.isums.maintainservice.domains.dtos.PlanHouseDTO.PlanHouseDto;
import com.isums.maintainservice.domains.entities.PeriodicInspectionPlan;
import com.isums.maintainservice.domains.entities.PlanHouse;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PlanMapper {
    PlanDto plan(PeriodicInspectionPlan plan);
    List<PlanDto> plans(List<PeriodicInspectionPlan> plans);

    PlanHouseDto planHouseDto(PlanHouse planHouse);
    List<PlanHouseDto> planHouseDtos(List<PlanHouse> planHouses);
}
