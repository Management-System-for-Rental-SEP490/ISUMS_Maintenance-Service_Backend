package com.isums.maintainservice.services;

import com.isums.maintainservice.domains.entities.MaintenanceJobHistory;
import com.isums.maintainservice.infrastructures.repositories.MaintenanceJobHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MaintenanceJobHistoryServiceImpl")
class MaintenanceJobHistoryServiceImplTest {

    @Mock private MaintenanceJobHistoryRepository repo;

    @InjectMocks private MaintenanceJobHistoryServiceImpl service;

    @Test
    @DisplayName("delegates to repository ordered by createdAt asc")
    void delegates() {
        UUID jobId = UUID.randomUUID();
        when(repo.findByJobIdOrderByCreatedAtAsc(jobId)).thenReturn(List.of());

        assertThat(service.getJobHistory(jobId)).isEmpty();
        verify(repo).findByJobIdOrderByCreatedAtAsc(jobId);
    }
}
