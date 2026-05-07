package com.isums.maintainservice.services;

import com.isums.maintainservice.domains.dtos.MaintainJobDTO.MaintenanceJobDto;
import com.isums.maintainservice.domains.entities.MaintenanceJob;
import com.isums.maintainservice.domains.entities.PeriodicInspectionPlan;
import com.isums.maintainservice.domains.entities.PlanHouse;
import com.isums.maintainservice.domains.enums.FrequencyType;
import com.isums.maintainservice.domains.enums.JobAction;
import com.isums.maintainservice.domains.enums.JobStatus;
import com.isums.maintainservice.domains.events.JobEvent;
import com.isums.maintainservice.exceptions.BadRequestException;
import com.isums.maintainservice.exceptions.NotFoundException;
import com.isums.maintainservice.infrastructures.gRpc.UserClientsGrpc;
import com.isums.maintainservice.infrastructures.kafka.JobEventProducer;
import com.isums.maintainservice.infrastructures.mappers.MaintenanceMapper;
import com.isums.maintainservice.infrastructures.repositories.MaintenanceJobHistoryRepository;
import com.isums.maintainservice.infrastructures.repositories.MaintenanceJobRepository;
import com.isums.maintainservice.infrastructures.repositories.PeriodicInspectionPlanRepository;
import com.isums.maintainservice.infrastructures.repositories.PlanHouseRepository;
import com.isums.userservice.grpc.UserResponse;
import common.paginations.cache.CachedPageService;
import common.paginations.dtos.PageRequest;
import common.paginations.dtos.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MaintenanceJobServiceImpl")
class MaintenanceJobServiceImplTest {

    @Mock private PeriodicInspectionPlanRepository periodicInspectionPlanRepository;
    @Mock private PlanHouseRepository planHouseRepository;
    @Mock private MaintenanceMapper maintenanceMapper;
    @Mock private MaintenanceJobRepository maintenanceJobRepository;
    @Mock private MaintenanceJobHistoryRepository historyRepository;
    @Mock private UserClientsGrpc userClientsGrpc;
    @Mock private JobEventProducer jobEventProducer;
    @Mock private CachedPageService cachedPageService;

    @InjectMocks private MaintenanceJobServiceImpl service;

    private UUID planId, houseId, jobId;

    @BeforeEach
    void setUp() {
        planId = UUID.randomUUID();
        houseId = UUID.randomUUID();
        jobId = UUID.randomUUID();
    }

    private PeriodicInspectionPlan plan(FrequencyType type, int val) {
        return PeriodicInspectionPlan.builder()
                .id(planId).name("p")
                .frequencyType(type).frequencyValue(val)
                .nextRunAt(LocalDate.now())
                .isActive(true).build();
    }

    @Nested
    @DisplayName("generateMaintainJobs (scheduled batch)")
    class Generate {

        @Test
        @DisplayName("creates one job per active plan house that has no existing job and publishes JOB_CREATED")
        void createsJobs() {
            PeriodicInspectionPlan p = plan(FrequencyType.MONTHLY, 1);
            PlanHouse h1 = PlanHouse.builder().planId(planId).houseId(houseId).build();

            when(periodicInspectionPlanRepository.findByNextRunAtLessThanEqualAndIsActiveTrue(any(LocalDate.class)))
                    .thenReturn(List.of(p));
            when(planHouseRepository.findByPlanId(planId)).thenReturn(List.of(h1));
            when(maintenanceJobRepository.findExistingHouseIds(planId, p.getNextRunAt(), List.of(houseId)))
                    .thenReturn(List.of());
            when(maintenanceJobRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            service.generateMaintainJobs();

            ArgumentCaptor<List<MaintenanceJob>> cap = ArgumentCaptor.forClass(List.class);
            verify(maintenanceJobRepository).saveAll(cap.capture());
            assertThat(cap.getValue()).hasSize(1);
            assertThat(cap.getValue().getFirst().getStatus()).isEqualTo(JobStatus.CREATED);
            assertThat(cap.getValue().getFirst().getHouseId()).isEqualTo(houseId);

            verify(jobEventProducer).publishJobCreated(any(JobEvent.class));
            // next run bumped by 1 month
            assertThat(p.getNextRunAt()).isEqualTo(LocalDate.now().plusMonths(1));
        }

        @Test
        @DisplayName("skips houses that already have a job for this period (idempotent)")
        void skipsExisting() {
            PeriodicInspectionPlan p = plan(FrequencyType.MONTHLY, 1);
            PlanHouse h1 = PlanHouse.builder().planId(planId).houseId(houseId).build();
            LocalDate originalNextRun = p.getNextRunAt();

            when(periodicInspectionPlanRepository.findByNextRunAtLessThanEqualAndIsActiveTrue(any()))
                    .thenReturn(List.of(p));
            when(planHouseRepository.findByPlanId(planId)).thenReturn(List.of(h1));
            when(maintenanceJobRepository.findExistingHouseIds(planId, originalNextRun, List.of(houseId)))
                    .thenReturn(List.of(houseId));

            service.generateMaintainJobs();

            verify(maintenanceJobRepository, never()).saveAll(any());
            verify(jobEventProducer, never()).publishJobCreated(any());
            // next-run not advanced because no new job was added
            assertThat(p.getNextRunAt()).isEqualTo(originalNextRun);
        }

        @Test
        @DisplayName("QUARTERLY advances nextRunAt by 3*value months")
        void quarterlyAdvance() {
            PeriodicInspectionPlan p = plan(FrequencyType.QUARTERLY, 2);
            LocalDate original = p.getNextRunAt();

            PlanHouse h1 = PlanHouse.builder().planId(planId).houseId(houseId).build();
            when(periodicInspectionPlanRepository.findByNextRunAtLessThanEqualAndIsActiveTrue(any()))
                    .thenReturn(List.of(p));
            when(planHouseRepository.findByPlanId(planId)).thenReturn(List.of(h1));
            when(maintenanceJobRepository.findExistingHouseIds(any(), any(), any())).thenReturn(List.of());
            when(maintenanceJobRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            service.generateMaintainJobs();

            assertThat(p.getNextRunAt()).isEqualTo(original.plusMonths(6)); // 3 * 2
        }

        @Test
        @DisplayName("returns empty list when no plans due today")
        void noPlans() {
            when(periodicInspectionPlanRepository.findByNextRunAtLessThanEqualAndIsActiveTrue(any()))
                    .thenReturn(List.of());

            List<MaintenanceJobDto> result = service.generateMaintainJobs();

            assertThat(result).isEmpty();
            verify(maintenanceJobRepository, never()).saveAll(any());
        }
    }

    @Nested
    @DisplayName("generateByPlan (manual trigger)")
    class GenerateByPlan {

        @Test
        @DisplayName("throws BadRequest when plan has no houses")
        void noHouses() {
            PeriodicInspectionPlan p = plan(FrequencyType.MONTHLY, 1);
            when(periodicInspectionPlanRepository.findById(planId)).thenReturn(Optional.of(p));
            when(planHouseRepository.findByPlanId(planId)).thenReturn(List.of());

            assertThatThrownBy(() -> service.generateByPlan(planId))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("throws BadRequest when all houses already have jobs for this period")
        void allExisting() {
            PeriodicInspectionPlan p = plan(FrequencyType.MONTHLY, 1);
            PlanHouse h1 = PlanHouse.builder().planId(planId).houseId(houseId).build();

            when(periodicInspectionPlanRepository.findById(planId)).thenReturn(Optional.of(p));
            when(planHouseRepository.findByPlanId(planId)).thenReturn(List.of(h1));
            when(maintenanceJobRepository.findExistingHouseIds(any(), any(), any()))
                    .thenReturn(List.of(houseId));

            assertThatThrownBy(() -> service.generateByPlan(planId))
                    .isInstanceOf(BadRequestException.class);
        }

        @Test
        @DisplayName("creates jobs and publishes JOB_CREATED for each")
        void happy() {
            PeriodicInspectionPlan p = plan(FrequencyType.MONTHLY, 1);
            PlanHouse h1 = PlanHouse.builder().planId(planId).houseId(houseId).build();
            PlanHouse h2 = PlanHouse.builder().planId(planId).houseId(UUID.randomUUID()).build();

            when(periodicInspectionPlanRepository.findById(planId)).thenReturn(Optional.of(p));
            when(planHouseRepository.findByPlanId(planId)).thenReturn(List.of(h1, h2));
            when(maintenanceJobRepository.findExistingHouseIds(any(), any(), any())).thenReturn(List.of());
            when(maintenanceJobRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

            service.generateByPlan(planId);

            verify(jobEventProducer, times(2)).publishJobCreated(any());
        }

        @Test
        @DisplayName("throws NotFoundException-like when plan missing")
        void missing() {
            when(periodicInspectionPlanRepository.findById(planId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.generateByPlan(planId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("planNotFound");
        }
    }

    @Nested
    @DisplayName("updateJobStatus — state machine")
    class UpdateJobStatus {

        @Test
        @DisplayName("SCHEDULED -> IN_PROGRESS transitions without publishing event")
        void scheduledToInProgress() {
            MaintenanceJob job = MaintenanceJob.builder()
                    .id(jobId).status(JobStatus.SCHEDULED).build();

            when(maintenanceJobRepository.findById(jobId)).thenReturn(Optional.of(job));
            when(maintenanceJobRepository.save(job)).thenReturn(job);

            service.updateJobStatus(jobId, JobStatus.IN_PROGRESS);

            assertThat(job.getStatus()).isEqualTo(JobStatus.IN_PROGRESS);
            verify(jobEventProducer, never()).publishJobCompleted(any());
        }

        @Test
        @DisplayName("IN_PROGRESS -> COMPLETED publishes JOB_COMPLETED and evicts cache")
        void inProgressToCompleted() {
            UUID slot = UUID.randomUUID(), staff = UUID.randomUUID();
            MaintenanceJob job = MaintenanceJob.builder()
                    .id(jobId).status(JobStatus.IN_PROGRESS)
                    .slotId(slot).assignedStaffId(staff).build();

            when(maintenanceJobRepository.findById(jobId)).thenReturn(Optional.of(job));
            when(maintenanceJobRepository.save(job)).thenReturn(job);

            service.updateJobStatus(jobId, JobStatus.COMPLETED);

            assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);

            ArgumentCaptor<JobEvent> evt = ArgumentCaptor.forClass(JobEvent.class);
            verify(jobEventProducer).publishJobCompleted(evt.capture());
            assertThat(evt.getValue().getAction()).isEqualTo(JobAction.JOB_COMPLETED);
            assertThat(evt.getValue().getReferenceType()).isEqualTo("MAINTENANCE");

            verify(cachedPageService).evictAll("maintenances-jobs-v3");
        }

        @Test
        @DisplayName("invalid transition -> BadRequestException (400), not 500")
        void invalidTransition() {
            MaintenanceJob job = MaintenanceJob.builder()
                    .id(jobId).status(JobStatus.CREATED).build();

            when(maintenanceJobRepository.findById(jobId)).thenReturn(Optional.of(job));

            assertThatThrownBy(() -> service.updateJobStatus(jobId, JobStatus.COMPLETED))
                    .isInstanceOf(BadRequestException.class);
            verify(maintenanceJobRepository, never()).save(any());
        }

        @Test
        @DisplayName("missing job -> NotFoundException")
        void missing() {
            when(maintenanceJobRepository.findById(jobId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateJobStatus(jobId, JobStatus.IN_PROGRESS))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("mark* kafka handlers")
    class Mark {

        @Test
        @DisplayName("markScheduled assigns staff+slot and writes history")
        void scheduled() {
            MaintenanceJob job = MaintenanceJob.builder()
                    .id(jobId).status(JobStatus.CREATED).build();
            UUID staff = UUID.randomUUID(), slot = UUID.randomUUID();
            JobEvent event = JobEvent.builder()
                    .referenceId(jobId).staffId(staff).slotId(slot)
                    .action(JobAction.JOB_SCHEDULED).build();

            when(maintenanceJobRepository.findById(jobId)).thenReturn(Optional.of(job));

            service.markScheduled(event);

            assertThat(job.getStatus()).isEqualTo(JobStatus.SCHEDULED);
            assertThat(job.getAssignedStaffId()).isEqualTo(staff);
            assertThat(job.getSlotId()).isEqualTo(slot);
            verify(historyRepository).save(any());
        }

        @Test
        @DisplayName("markScheduled is idempotent if already SCHEDULED")
        void scheduledIdempotent() {
            MaintenanceJob job = MaintenanceJob.builder()
                    .id(jobId).status(JobStatus.SCHEDULED).build();
            JobEvent event = JobEvent.builder().referenceId(jobId).build();

            when(maintenanceJobRepository.findById(jobId)).thenReturn(Optional.of(job));

            service.markScheduled(event);

            verify(maintenanceJobRepository, never()).save(any());
        }

        @Test
        @DisplayName("markRescheduled re-assigns staff+slot (even if SCHEDULED already)")
        void rescheduled() {
            UUID newStaff = UUID.randomUUID(), newSlot = UUID.randomUUID();
            MaintenanceJob job = MaintenanceJob.builder()
                    .id(jobId).status(JobStatus.NEED_RESCHEDULE)
                    .assignedStaffId(UUID.randomUUID()).build();
            JobEvent event = JobEvent.builder()
                    .referenceId(jobId).staffId(newStaff).slotId(newSlot)
                    .action(JobAction.JOB_RESCHEDULED).build();

            when(maintenanceJobRepository.findById(jobId)).thenReturn(Optional.of(job));

            service.markRescheduled(event);

            assertThat(job.getStatus()).isEqualTo(JobStatus.SCHEDULED);
            assertThat(job.getAssignedStaffId()).isEqualTo(newStaff);
            assertThat(job.getSlotId()).isEqualTo(newSlot);
        }

        @Test
        @DisplayName("markNeedReschedule flips status")
        void needReschedule() {
            MaintenanceJob job = MaintenanceJob.builder()
                    .id(jobId).status(JobStatus.SCHEDULED).build();
            JobEvent event = JobEvent.builder()
                    .referenceId(jobId).action(JobAction.JOB_NEED_RESCHEDULE).build();

            when(maintenanceJobRepository.findById(jobId)).thenReturn(Optional.of(job));

            service.markNeedReschedule(event);

            assertThat(job.getStatus()).isEqualTo(JobStatus.NEED_RESCHEDULE);
        }

        @Test
        @DisplayName("markSlot sets slotId when null; no-op when already set")
        void markSlot() {
            MaintenanceJob job = MaintenanceJob.builder()
                    .id(jobId).status(JobStatus.CREATED).build();
            UUID slot = UUID.randomUUID();
            JobEvent event = JobEvent.builder()
                    .referenceId(jobId).slotId(slot).action(JobAction.JOB_ASSIGNED).build();

            when(maintenanceJobRepository.findById(jobId)).thenReturn(Optional.of(job));

            service.markSlot(event);

            assertThat(job.getSlotId()).isEqualTo(slot);

            // second call — slot already set, should skip
            UUID laterSlot = UUID.randomUUID();
            JobEvent later = JobEvent.builder().referenceId(jobId).slotId(laterSlot).build();
            when(maintenanceJobRepository.findById(jobId)).thenReturn(Optional.of(job));

            service.markSlot(later);

            assertThat(job.getSlotId()).isEqualTo(slot); // unchanged
        }
    }

    @Nested
    @DisplayName("staff enrichment")
    class StaffEnrichment {

        @Test
        @DisplayName("getJobById returns full staff object when staff assigned")
        void getJobByIdReturnsStaffObject() {
            UUID staffId = UUID.randomUUID();
            MaintenanceJob job = MaintenanceJob.builder()
                    .id(jobId)
                    .planId(planId)
                    .houseId(houseId)
                    .assignedStaffId(staffId)
                    .periodStartDate(LocalDate.now())
                    .status(JobStatus.SCHEDULED)
                    .build();

            when(maintenanceJobRepository.findById(jobId)).thenReturn(Optional.of(job));
            when(userClientsGrpc.getUser(staffId.toString())).thenReturn(staffResponse(staffId, "An Staff"));

            MaintenanceJobDto result = service.getJobById(jobId);

            assertThat(result.assignedStaffId()).isEqualTo(staffId);
            assertThat(result.staffName()).isEqualTo("An Staff");
            assertThat(result.staffPhone()).isEqualTo("0909000111");
            assertThat(result.staff()).isNotNull();
            assertThat(result.staff().email()).isEqualTo("an.staff@isums.pro");
            assertThat(result.staff().roles()).containsExactly("TECHNICAL_STAFF");
        }

        @Test
        @DisplayName("getAll enriches paged jobs with staff object")
        void getAllEnrichesPagedJobs() {
            UUID staffId = UUID.randomUUID();
            PageRequest request = new PageRequest(1, 10, null, null, null);
            MaintenanceJob job = MaintenanceJob.builder()
                    .id(jobId)
                    .planId(planId)
                    .houseId(houseId)
                    .assignedStaffId(staffId)
                    .periodStartDate(LocalDate.now())
                    .status(JobStatus.COMPLETED)
                    .build();

            org.springframework.data.domain.Page<MaintenanceJob> page =
                    new org.springframework.data.domain.PageImpl<>(List.of(job));

            doAnswer(inv -> ((Supplier<PageResponse<MaintenanceJobDto>>) inv.getArgument(3)).get())
                    .when(cachedPageService)
                    .getOrLoad(anyString(), any(), any(), any());
            when(maintenanceJobRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                    .thenReturn(page);
            when(userClientsGrpc.getUser(staffId.toString())).thenReturn(staffResponse(staffId, "Binh Staff"));

            PageResponse<MaintenanceJobDto> result = service.getAll(request);

            assertThat(result.items()).hasSize(1);
            assertThat(result.items().getFirst().staffName()).isEqualTo("Binh Staff");
            assertThat(result.items().getFirst().staff()).isNotNull();
            assertThat(result.items().getFirst().staff().phoneNumber()).isEqualTo("0909000111");
        }
    }

    private MaintenanceJobDto stubDto(UUID id) {
        return new MaintenanceJobDto(id, planId, houseId, null, null, null, null, LocalDate.now(), JobStatus.CREATED);
    }

    private UserResponse staffResponse(UUID staffId, String name) {
        return UserResponse.newBuilder()
                .setId(staffId.toString())
                .setKeycloakId("kc-" + staffId)
                .setName(name)
                .setEmail(name.toLowerCase().replace(" ", ".") + "@isums.pro")
                .setPhoneNumber("0909000111")
                .setIsEnabled(true)
                .addRoles("TECHNICAL_STAFF")
                .build();
    }
}
