package com.isums.maintainservice.domains.dtos;

import com.isums.maintainservice.domains.enums.InspectionStatus;

public record UpdateInspectionRequest(
        InspectionStatus status
) {

}
