package com.isums.maintainservice.services;

import com.isums.maintainservice.domains.dtos.MaintenanceExecution.CreateExecutionRequest;
import com.isums.maintainservice.domains.dtos.MaintenanceExecution.ExecutionDto;
import com.isums.maintainservice.domains.entities.MaintenanceExecution;
import com.isums.maintainservice.domains.entities.MaintenanceJob;
import com.isums.maintainservice.domains.events.AssetConditionEvent;
import com.isums.maintainservice.exceptions.NotFoundException;
import com.isums.maintainservice.infrastructures.abstracts.MaintenanceExecutionService;
import com.isums.maintainservice.infrastructures.abstracts.MaintenanceJobService;
import com.isums.maintainservice.infrastructures.gRpc.UserClientsGrpc;
import com.isums.maintainservice.infrastructures.i18n.MaintenanceMessageKeys;
import com.isums.maintainservice.infrastructures.kafka.AssetConditionProducer;
import com.isums.maintainservice.infrastructures.mappers.MaintenanceMapper;
import com.isums.maintainservice.infrastructures.repositories.MaintenanceExecutionRepository;
import com.isums.maintainservice.infrastructures.repositories.MaintenanceJobRepository;
import common.i18n.TranslationMap;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaintenanceExecutionServiceImpl implements MaintenanceExecutionService {
    private static final String DEFAULT_LANGUAGE = "vi";

    private final MaintenanceJobRepository maintenanceJobRepository;
    private final MaintenanceExecutionRepository maintenanceExecutionRepository;
    private final MaintenanceMapper maintenanceMapper;
    private final AssetConditionProducer assetConditionProducer;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final UserClientsGrpc userClientsGrpc;
    private final TranslationAutoFillService translationAutoFillService;
    @Override
    public ExecutionDto createExecution(String staffId, CreateExecutionRequest req) {
        try{
            MaintenanceJob job = maintenanceJobRepository.findById(req.jobId())
                    .orElseThrow(() -> new NotFoundException(MaintenanceMessageKeys.JOB_NOT_FOUND));

            String sourceLanguage = resolveUserLanguage(staffId);
            TranslationMap notesTranslations = translationAutoFillService.complete(req.notes(), sourceLanguage);

            MaintenanceExecution ex = MaintenanceExecution.builder()
                    .job(job)
                    .houseId(req.houseId())
                    .assetId(req.assetId())
                    .staffId(UUID.fromString(staffId))
                    .conditionScore(req.conditionScore())
                    .notes(req.notes())
                    .notesTranslations(notesTranslations)
                    .sourceLanguage(sourceLanguage)
                    .createdAt(Instant.now())
                    .build();

            MaintenanceExecution created = maintenanceExecutionRepository.save(ex);
            AssetConditionEvent event = AssetConditionEvent.builder()
                    .assetId(req.assetId())
                    .conditionScore(req.conditionScore())
                    .build();

            assetConditionProducer.sendConditionUpdate(event);

            return maintenanceMapper.ex(created);

        } catch (NotFoundException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException(MaintenanceMessageKeys.CANNOT_CREATE_EXECUTION + ": " + ex.getMessage());
        }
    }

    @Override
    public List<ExecutionDto> getExecutionsByJobId(UUID jobId) {
        try{
            List<MaintenanceExecution> executions = maintenanceExecutionRepository.findByJobId(jobId);
            return maintenanceMapper.exs(executions);

        } catch (Exception ex) {
            throw new RuntimeException(MaintenanceMessageKeys.CANNOT_GET_EXECUTIONS_BY_JOB + ": " + ex.getMessage());
        }
    }

    @Override
    public List<ExecutionDto> getAllExecutions() {
        try{
            List<MaintenanceExecution> executions = maintenanceExecutionRepository.findAll();
            return maintenanceMapper.exs(executions);

        } catch (Exception ex) {
            throw new RuntimeException(MaintenanceMessageKeys.CANNOT_GET_EXECUTIONS + ": " + ex.getMessage());
        }
    }

    @Override
    public List<ExecutionDto> getExecutionsByHouseId(UUID houseId) {
        try{
            List<MaintenanceExecution> executions = maintenanceExecutionRepository.findByHouseIdOrderByCreatedAtDesc(houseId);
            return maintenanceMapper.exs(executions);
        } catch (Exception ex){
            throw new RuntimeException(MaintenanceMessageKeys.CANNOT_GET_EXECUTIONS_BY_HOUSE + ": " + ex.getMessage());
        }
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
}
