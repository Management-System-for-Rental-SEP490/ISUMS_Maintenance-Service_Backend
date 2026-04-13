package com.isums.maintainservice.services;

import com.isums.maintainservice.domains.dtos.CreateInspectionRequest;
import com.isums.maintainservice.domains.dtos.InspectionDto;
import com.isums.maintainservice.domains.entities.InspectionJob;
import com.isums.maintainservice.domains.enums.InspectionStatus;
import com.isums.maintainservice.domains.enums.InspectionType;
import com.isums.maintainservice.domains.enums.JobAction;
import com.isums.maintainservice.domains.events.JobCreatedEvent;
import com.isums.maintainservice.domains.events.JobEvent;
import com.isums.maintainservice.exceptions.BadRequestException;
import com.isums.maintainservice.exceptions.NotFoundException;
import com.isums.maintainservice.infrastructures.gRpc.UserClientsGrpc;
import com.isums.maintainservice.infrastructures.kafka.JobEventProducer;
import com.isums.maintainservice.infrastructures.mappers.InspectionMapper;
import com.isums.maintainservice.infrastructures.repositories.InspectionJobRepository;
import com.isums.maintainservice.infrastructures.repositories.MaintenanceJobHistoryRepository;
import com.isums.userservice.grpc.UserResponse;
import common.paginations.cache.CachedPageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InspectionJobServiceImpl")
class InspectionJobServiceImplTest {

    @Mock private InspectionJobRepository inspectionJobRepository;
    @Mock private InspectionMapper mapper;
    @Mock private JobEventProducer jobEventProducer;
    @Mock private MaintenanceJobHistoryRepository historyRepository;
    @Mock private UserClientsGrpc userClientsGrpc;
    @Mock private CachedPageService cachedPageService;

    @InjectMocks private InspectionJobServiceImpl service;

    private UUID houseId, jobId;

    @BeforeEach
    void setUp() {
        houseId = UUID.randomUUID();
        jobId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("saves a CREATED inspection and maps result")
        void happy() {
            CreateInspectionRequest req = new CreateInspectionRequest(houseId, InspectionType.CHECK_IN, "note");

            when(inspectionJobRepository.save(any(InspectionJob.class)))
                    .thenAnswer(inv -> { InspectionJob j = inv.getArgument(0); j.setId(jobId); return j; });
            when(mapper.toDto(any(InspectionJob.class))).thenReturn(stubDto(jobId));

            InspectionDto dto = service.create(req);

            ArgumentCaptor<InspectionJob> cap = ArgumentCaptor.forClass(InspectionJob.class);
            verify(inspectionJobRepository).save(cap.capture());
            assertThat(cap.getValue().getStatus()).isEqualTo(InspectionStatus.CREATED);
            assertThat(cap.getValue().getHouseId()).isEqualTo(houseId);
            assertThat(cap.getValue().getType()).isEqualTo(InspectionType.CHECK_IN);
            assertThat(dto.id()).isEqualTo(jobId);
        }
    }

    @Nested
    @DisplayName("createFromEvent")
    class CreateFromEvent {

        @Test
        @DisplayName("CHECK_IN event creates inspection with handover note")
        void checkIn() {
            UUID contractId = UUID.randomUUID();
            JobCreatedEvent event = JobCreatedEvent.builder()
                    .referenceId(contractId).houseId(houseId)
                    .type("CHECK_IN").referenceType("INSPECTION").build();

            when(inspectionJobRepository.save(any(InspectionJob.class)))
                    .thenAnswer(inv -> { InspectionJob j = inv.getArgument(0); j.setId(jobId); return j; });
            when(mapper.toDto(any(InspectionJob.class))).thenReturn(stubDto(jobId));

            service.createFromEvent(event);

            ArgumentCaptor<InspectionJob> cap = ArgumentCaptor.forClass(InspectionJob.class);
            verify(inspectionJobRepository).save(cap.capture());
            assertThat(cap.getValue().getType()).isEqualTo(InspectionType.CHECK_IN);
            assertThat(cap.getValue().getContractId()).isEqualTo(contractId);
            assertThat(cap.getValue().getNote()).contains("trước khi bàn giao");
            assertThat(cap.getValue().getStatus()).isEqualTo(InspectionStatus.CREATED);
        }

        @Test
        @DisplayName("non-CHECK_IN event creates inspection with end-of-contract note")
        void checkOut() {
            JobCreatedEvent event = JobCreatedEvent.builder()
                    .referenceId(UUID.randomUUID()).houseId(houseId)
                    .type("CHECK_OUT").referenceType("INSPECTION").build();

            when(inspectionJobRepository.save(any(InspectionJob.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(mapper.toDto(any(InspectionJob.class))).thenReturn(stubDto(UUID.randomUUID()));

            service.createFromEvent(event);

            ArgumentCaptor<InspectionJob> cap = ArgumentCaptor.forClass(InspectionJob.class);
            verify(inspectionJobRepository).save(cap.capture());
            assertThat(cap.getValue().getType()).isEqualTo(InspectionType.CHECK_OUT);
            assertThat(cap.getValue().getNote()).contains("kết thúc hợp đồng");
        }
    }

    @Nested
    @DisplayName("getInspectionById")
    class GetById {

        @Test
        @DisplayName("returns DTO with staff name+phone when staff is assigned")
        void withStaff() {
            UUID staff = UUID.randomUUID();
            InspectionJob job = InspectionJob.builder()
                    .id(jobId).houseId(houseId).assignedStaffId(staff)
                    .status(InspectionStatus.SCHEDULED).type(InspectionType.CHECK_IN).build();

            when(inspectionJobRepository.findById(jobId)).thenReturn(Optional.of(job));
            when(userClientsGrpc.getUser(staff.toString()))
                    .thenReturn(UserResponse.newBuilder().setId(staff.toString())
                            .setName("Alice").setPhoneNumber("0900").build());

            InspectionDto dto = service.getInspectionById(jobId);

            assertThat(dto.staffName()).isEqualTo("Alice");
            assertThat(dto.staffPhone()).isEqualTo("0900");
        }

        @Test
        @DisplayName("returns DTO without staff lookup when not assigned yet")
        void withoutStaff() {
            InspectionJob job = InspectionJob.builder()
                    .id(jobId).houseId(houseId).status(InspectionStatus.CREATED)
                    .type(InspectionType.CHECK_IN).build();
            when(inspectionJobRepository.findById(jobId)).thenReturn(Optional.of(job));

            InspectionDto dto = service.getInspectionById(jobId);

            assertThat(dto.staffName()).isNull();
            verify(userClientsGrpc, never()).getUser(any());
        }

        @Test
        @DisplayName("wraps when inspection missing")
        void missing() {
            when(inspectionJobRepository.findById(jobId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getInspectionById(jobId))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("updateStatus — state machine")
    class UpdateStatus {

        @Test
        @DisplayName("SCHEDULED -> IN_PROGRESS transitions cleanly")
        void scheduledToInProgress() {
            InspectionJob job = InspectionJob.builder()
                    .id(jobId).status(InspectionStatus.SCHEDULED).build();

            when(inspectionJobRepository.findById(jobId)).thenReturn(Optional.of(job));
            when(inspectionJobRepository.save(job)).thenReturn(job);
            when(mapper.toDto(job)).thenReturn(stubDto(jobId));

            service.updateStatus(jobId, InspectionStatus.IN_PROGRESS);

            assertThat(job.getStatus()).isEqualTo(InspectionStatus.IN_PROGRESS);
            verify(jobEventProducer, never()).publishJobCompleted(any());
        }

        @Test
        @DisplayName("IN_PROGRESS -> DONE publishes JOB_COMPLETED and evicts cache")
        void inProgressToDone() {
            UUID slot = UUID.randomUUID(), staff = UUID.randomUUID();
            InspectionJob job = InspectionJob.builder()
                    .id(jobId).status(InspectionStatus.IN_PROGRESS)
                    .slotId(slot).assignedStaffId(staff).build();

            when(inspectionJobRepository.findById(jobId)).thenReturn(Optional.of(job));
            when(inspectionJobRepository.save(job)).thenReturn(job);
            when(mapper.toDto(job)).thenReturn(stubDto(jobId));

            service.updateStatus(jobId, InspectionStatus.DONE);

            assertThat(job.getStatus()).isEqualTo(InspectionStatus.DONE);

            ArgumentCaptor<JobEvent> evt = ArgumentCaptor.forClass(JobEvent.class);
            verify(jobEventProducer).publishJobCompleted(evt.capture());
            assertThat(evt.getValue().getAction()).isEqualTo(JobAction.JOB_COMPLETED);
            assertThat(evt.getValue().getReferenceType()).isEqualTo("INSPECTION");
            assertThat(evt.getValue().getSlotId()).isEqualTo(slot);
            assertThat(evt.getValue().getStaffId()).isEqualTo(staff);

            verify(cachedPageService).evictAll("inspections");
        }

        @Test
        @DisplayName("DONE -> APPROVED transitions without publishing event")
        void doneToApproved() {
            InspectionJob job = InspectionJob.builder()
                    .id(jobId).status(InspectionStatus.DONE).build();

            when(inspectionJobRepository.findById(jobId)).thenReturn(Optional.of(job));
            when(inspectionJobRepository.save(job)).thenReturn(job);
            when(mapper.toDto(job)).thenReturn(stubDto(jobId));

            service.updateStatus(jobId, InspectionStatus.APPROVED);

            assertThat(job.getStatus()).isEqualTo(InspectionStatus.APPROVED);
            verify(jobEventProducer, never()).publishJobCompleted(any());
        }

        @Test
        @DisplayName("invalid transition -> BadRequestException (400), not 500")
        void invalidTransition() {
            InspectionJob job = InspectionJob.builder()
                    .id(jobId).status(InspectionStatus.CREATED).build();

            when(inspectionJobRepository.findById(jobId)).thenReturn(Optional.of(job));

            assertThatThrownBy(() ->
                    service.updateStatus(jobId, InspectionStatus.APPROVED))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Invalid");
        }

        @Test
        @DisplayName("missing job -> NotFoundException (404), not 500")
        void missing() {
            when(inspectionJobRepository.findById(jobId)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    service.updateStatus(jobId, InspectionStatus.DONE))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("markScheduled")
    class MarkScheduled {

        @Test
        @DisplayName("flips CREATED -> SCHEDULED and saves history")
        void flips() {
            UUID staff = UUID.randomUUID(), slot = UUID.randomUUID();
            InspectionJob job = InspectionJob.builder()
                    .id(jobId).status(InspectionStatus.CREATED).build();
            JobEvent event = JobEvent.builder()
                    .referenceId(jobId).staffId(staff).slotId(slot)
                    .action(JobAction.JOB_SCHEDULED).build();

            when(inspectionJobRepository.findById(jobId)).thenReturn(Optional.of(job));

            service.markScheduled(event);

            assertThat(job.getStatus()).isEqualTo(InspectionStatus.SCHEDULED);
            assertThat(job.getAssignedStaffId()).isEqualTo(staff);
            assertThat(job.getSlotId()).isEqualTo(slot);
            verify(inspectionJobRepository).save(job);
            verify(historyRepository).save(any());
        }

        @Test
        @DisplayName("is idempotent when already SCHEDULED")
        void idempotent() {
            InspectionJob job = InspectionJob.builder()
                    .id(jobId).status(InspectionStatus.SCHEDULED).build();
            JobEvent event = JobEvent.builder().referenceId(jobId).build();

            when(inspectionJobRepository.findById(jobId)).thenReturn(Optional.of(job));

            service.markScheduled(event);

            verify(inspectionJobRepository, never()).save(any());
            verify(historyRepository, never()).save(any());
        }
    }

    private InspectionDto stubDto(UUID id) {
        return new InspectionDto(id, houseId, null, null, null, null,
                InspectionStatus.CREATED, InspectionType.CHECK_IN, "x", null, null);
    }
}
