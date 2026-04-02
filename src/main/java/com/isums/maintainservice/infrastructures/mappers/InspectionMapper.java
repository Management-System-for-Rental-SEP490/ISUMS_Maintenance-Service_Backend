package com.isums.maintainservice.infrastructures.mappers;

import com.isums.maintainservice.domains.dtos.InspectionDto;
import com.isums.maintainservice.domains.dtos.MaintainJobDTO.MaintenanceJobDto;
import com.isums.maintainservice.domains.entities.InspectionJob;
import com.isums.maintainservice.domains.entities.MaintenanceJob;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface InspectionMapper {
    InspectionDto toDto(InspectionJob job);
    List<InspectionDto> toDtos(List<InspectionJob> jobs);
}
