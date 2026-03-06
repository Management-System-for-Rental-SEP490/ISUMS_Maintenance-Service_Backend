package com.isums.maintainservice.infrastructures.mappers;

import com.isums.maintainservice.domains.dtos.MaintainJobDTO.MaintenanceJobDto;
import com.isums.maintainservice.domains.entities.MaintenanceJob;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface MaintenanceMapper {
    MaintenanceJobDto job(MaintenanceJob job);
    List<MaintenanceJobDto> jobs(List<MaintenanceJob> jobs);
}
