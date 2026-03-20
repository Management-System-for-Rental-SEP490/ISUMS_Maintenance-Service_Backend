package com.isums.maintainservice.services;

import com.isums.maintainservice.domains.entities.MaintenanceJobHistory;
import com.isums.maintainservice.infrastructures.abstracts.MaintenanceJobHistoryService;
import com.isums.maintainservice.infrastructures.repositories.MaintenanceJobHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaintenanceJobHistoryServiceImpl implements MaintenanceJobHistoryService {
    private final MaintenanceJobHistoryRepository maintenanceJobHistoryRepository;
    @Override
    public List<MaintenanceJobHistory> getJobHistory(UUID jobId) {
        try{
            return maintenanceJobHistoryRepository.findByJobIdOrderByCreatedAtAsc(jobId);
        } catch (Exception ex){
            throw new RuntimeException("Can't get job history " + ex.getMessage());
        }
    }
}
