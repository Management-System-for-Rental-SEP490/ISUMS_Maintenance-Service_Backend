package com.isums.maintainservice.domains.dtos.MaintainJobDTO;

import java.util.List;
import java.util.UUID;

public record MaintenanceJobStaffDto(
        UUID id,
        String keycloakId,
        String name,
        String email,
        String phoneNumber,
        List<String> roles,
        Boolean enabled
) {
}
