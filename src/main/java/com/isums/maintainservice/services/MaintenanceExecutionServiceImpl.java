package com.isums.maintainservice.services;

import com.isums.maintainservice.domains.dtos.MaintenanceExecution.CreateExecutionRequest;
import com.isums.maintainservice.domains.dtos.MaintenanceExecution.ExecutionDto;
import com.isums.maintainservice.domains.entities.MaintenanceExecution;
import com.isums.maintainservice.domains.entities.MaintenanceJob;
import com.isums.maintainservice.domains.events.AssetConditionEvent;
import com.isums.maintainservice.infrastructures.abstracts.MaintenanceExecutionService;
import com.isums.maintainservice.infrastructures.abstracts.MaintenanceJobService;
import com.isums.maintainservice.infrastructures.kafka.AssetConditionProducer;
import com.isums.maintainservice.infrastructures.mappers.MaintenanceMapper;
import com.isums.maintainservice.infrastructures.repositories.MaintenanceExecutionRepository;
import com.isums.maintainservice.infrastructures.repositories.MaintenanceJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaintenanceExecutionServiceImpl implements MaintenanceExecutionService {
    private final MaintenanceJobRepository maintenanceJobRepository;
    private final MaintenanceExecutionRepository maintenanceExecutionRepository;
    private final MaintenanceMapper maintenanceMapper;
    private final AssetConditionProducer assetConditionProducer;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    @Override
    public ExecutionDto createExecution(UUID staffId, CreateExecutionRequest req) {
        try{
            MaintenanceJob job = maintenanceJobRepository.findById(req.jobId())
                    .orElseThrow(() -> new RuntimeException("Id not found"));

            MaintenanceExecution ex = MaintenanceExecution.builder()
                    .job(job)
                    .houseId(req.houseId())
                    .assetId(req.assetId())
                    .staffId(staffId)
                    .conditionScore(req.conditionScore())
                    .notes(req.notes())
                    .createdAt(Instant.now())
                    .build();

            MaintenanceExecution created = maintenanceExecutionRepository.save(ex);
            AssetConditionEvent event = AssetConditionEvent.builder()
                    .assetId(req.assetId())
                    .conditionScore(req.conditionScore())
                    .build();

            assetConditionProducer.sendConditionUpdate(event);

            return maintenanceMapper.ex(created);

        } catch (Exception ex) {
            throw new RuntimeException("Can't not create execution" + ex.getMessage());
        }
    }

    @Override
    public List<ExecutionDto> getExecutionsByJobId(UUID jobId) {
        try{
            List<MaintenanceExecution> executions = maintenanceExecutionRepository.findByJobId(jobId);
            return maintenanceMapper.exs(executions);

        } catch (Exception ex) {
            throw new RuntimeException("Can't get all executions by job" + ex.getMessage());
        }
    }

    @Override
    public List<ExecutionDto> getAllExecutions() {
        try{
            List<MaintenanceExecution> executions = maintenanceExecutionRepository.findAll();
            return maintenanceMapper.exs(executions);

        } catch (Exception ex) {
            throw new RuntimeException("Can't get all executions " + ex.getMessage());
        }
    }

    @Override
    public List<ExecutionDto> getExecutionsByHouseId(UUID houseId) {
        try{
            List<MaintenanceExecution> executions = maintenanceExecutionRepository.findByHouseIdOrderByCreatedAtDesc(houseId);
            return maintenanceMapper.exs(executions);
        } catch (Exception ex){
            throw new RuntimeException("Can't get executions by house " + ex.getMessage());
        }
    }
}
