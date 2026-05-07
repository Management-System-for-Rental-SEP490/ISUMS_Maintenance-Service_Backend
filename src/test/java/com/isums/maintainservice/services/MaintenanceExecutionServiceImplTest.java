package com.isums.maintainservice.services;

import com.isums.maintainservice.domains.dtos.MaintenanceExecution.CreateExecutionRequest;
import com.isums.maintainservice.domains.dtos.MaintenanceExecution.ExecutionDto;
import com.isums.maintainservice.domains.entities.MaintenanceExecution;
import com.isums.maintainservice.domains.entities.MaintenanceJob;
import com.isums.maintainservice.domains.events.AssetConditionEvent;
import com.isums.maintainservice.infrastructures.gRpc.UserClientsGrpc;
import com.isums.maintainservice.infrastructures.kafka.AssetConditionProducer;
import com.isums.maintainservice.infrastructures.mappers.MaintenanceMapper;
import com.isums.maintainservice.infrastructures.repositories.MaintenanceExecutionRepository;
import com.isums.maintainservice.infrastructures.repositories.MaintenanceJobRepository;
import com.isums.userservice.grpc.UserResponse;
import common.i18n.TranslationMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MaintenanceExecutionServiceImpl")
class MaintenanceExecutionServiceImplTest {

    @Mock private MaintenanceJobRepository maintenanceJobRepository;
    @Mock private MaintenanceExecutionRepository maintenanceExecutionRepository;
    @Mock private MaintenanceMapper maintenanceMapper;
    @Mock private AssetConditionProducer assetConditionProducer;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private UserClientsGrpc userClientsGrpc;
    @Mock private TranslationAutoFillService translationAutoFillService;

    @InjectMocks private MaintenanceExecutionServiceImpl service;

    private UUID jobId, houseId, assetId, staffId;

    @BeforeEach
    void setUp() {
        jobId = UUID.randomUUID();
        houseId = UUID.randomUUID();
        assetId = UUID.randomUUID();
        staffId = UUID.randomUUID();
        // createExecution now resolves the caller's language and fills the
        // notes translation map. Lenient stubs so non-create tests don't
        // trip MockitoExtension's strict stubbing.
        lenient().when(userClientsGrpc.getUser(anyString()))
                .thenReturn(UserResponse.newBuilder().setLanguage("vi").build());
        lenient().when(translationAutoFillService.complete(any(), anyString()))
                .thenReturn(new TranslationMap());
    }

    @Test
    @DisplayName("createExecution saves execution and publishes AssetConditionEvent")
    void create() {
        MaintenanceJob job = MaintenanceJob.builder().id(jobId).houseId(houseId).build();
        CreateExecutionRequest req = new CreateExecutionRequest(jobId, houseId, assetId, 8, "ok");

        when(maintenanceJobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(maintenanceExecutionRepository.save(any(MaintenanceExecution.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(maintenanceMapper.ex(any(MaintenanceExecution.class)))
                .thenReturn(stubDto());

        service.createExecution(staffId.toString(), req);

        ArgumentCaptor<MaintenanceExecution> exCap = ArgumentCaptor.forClass(MaintenanceExecution.class);
        verify(maintenanceExecutionRepository).save(exCap.capture());
        assertThat(exCap.getValue().getJob()).isEqualTo(job);
        assertThat(exCap.getValue().getStaffId()).isEqualTo(staffId);
        assertThat(exCap.getValue().getConditionScore()).isEqualTo(8);

        ArgumentCaptor<AssetConditionEvent> evtCap = ArgumentCaptor.forClass(AssetConditionEvent.class);
        verify(assetConditionProducer).sendConditionUpdate(evtCap.capture());
        assertThat(evtCap.getValue().getAssetId()).isEqualTo(assetId);
        assertThat(evtCap.getValue().getConditionScore()).isEqualTo(8);
    }

    @Test
    @DisplayName("createExecution wraps when staffId not a UUID")
    void badStaffId() {
        when(maintenanceJobRepository.findById(jobId))
                .thenReturn(Optional.of(MaintenanceJob.builder().id(jobId).build()));

        CreateExecutionRequest req = new CreateExecutionRequest(jobId, houseId, assetId, 5, null);

        assertThatThrownBy(() -> service.createExecution("not-a-uuid", req))
                .isInstanceOf(RuntimeException.class);
        verify(assetConditionProducer, never()).sendConditionUpdate(any());
    }

    @Test
    @DisplayName("createExecution wraps when job missing")
    void jobMissing() {
        when(maintenanceJobRepository.findById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createExecution(
                staffId.toString(),
                new CreateExecutionRequest(jobId, houseId, assetId, 5, null)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("getExecutionsByJobId passes jobId to repository")
    void byJob() {
        when(maintenanceExecutionRepository.findByJobId(jobId)).thenReturn(List.of());
        when(maintenanceMapper.exs(anyList())).thenReturn(List.of());

        assertThat(service.getExecutionsByJobId(jobId)).isEmpty();
        verify(maintenanceExecutionRepository).findByJobId(jobId);
    }

    @Test
    @DisplayName("getAllExecutions returns all via mapper")
    void getAll() {
        when(maintenanceExecutionRepository.findAll()).thenReturn(List.of());
        when(maintenanceMapper.exs(anyList())).thenReturn(List.of());

        assertThat(service.getAllExecutions()).isEmpty();
    }

    @Test
    @DisplayName("getExecutionsByHouseId returns ordered list")
    void byHouse() {
        when(maintenanceExecutionRepository.findByHouseIdOrderByCreatedAtDesc(houseId))
                .thenReturn(List.of());
        when(maintenanceMapper.exs(anyList())).thenReturn(List.of());

        assertThat(service.getExecutionsByHouseId(houseId)).isEmpty();
    }

    private ExecutionDto stubDto() {
        return new ExecutionDto(UUID.randomUUID(), jobId, houseId, assetId,
                staffId, 8, "ok", Instant.now());
    }
}
