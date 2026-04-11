package com.isums.maintainservice.services;

import com.isums.maintainservice.domains.dtos.CreateInspectionRequest;
import com.isums.maintainservice.domains.dtos.InspectionDto;
import com.isums.maintainservice.domains.entities.InspectionJob;
import com.isums.maintainservice.domains.entities.MaintenanceJob;
import com.isums.maintainservice.domains.entities.MaintenanceJobHistory;
import com.isums.maintainservice.domains.enums.InspectionStatus;
import com.isums.maintainservice.domains.enums.InspectionType;
import com.isums.maintainservice.domains.enums.JobAction;
import com.isums.maintainservice.domains.enums.JobStatus;
import com.isums.maintainservice.domains.events.JobCreatedEvent;
import com.isums.maintainservice.domains.events.JobEvent;
import com.isums.maintainservice.infrastructures.abstracts.InspectionJobService;
import com.isums.maintainservice.infrastructures.gRpc.UserClientsGrpc;
import com.isums.maintainservice.infrastructures.kafka.JobEventProducer;
import com.isums.maintainservice.infrastructures.mappers.InspectionMapper;
import com.isums.maintainservice.infrastructures.repositories.InspectionJobRepository;
import com.isums.maintainservice.infrastructures.repositories.MaintenanceJobHistoryRepository;
import common.paginations.cache.CachedPageService;
import common.paginations.converters.SpringPageConverter;
import common.paginations.dtos.PageRequest;
import common.paginations.dtos.PageResponse;
import common.paginations.specifications.SpecificationBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InspectionJobServiceImpl implements InspectionJobService {
    private final InspectionJobRepository inspectionJobRepository;
    private final InspectionMapper mapper;
    private final JobEventProducer jobEventProducer;
    private final MaintenanceJobHistoryRepository historyRepository;
    private final UserClientsGrpc userClientsGrpc;
    private final CachedPageService cachedPageService;

    private static final String PAGE_NS = "inspections";
    private static final Duration PAGE_TTL = Duration.ofMinutes(60);


    @Override
    public InspectionDto create(CreateInspectionRequest request) {
        try {

            InspectionJob job = InspectionJob.builder()
                    .houseId(request.houseId())
                    .type(request.type())
                    .note(request.note())
                    .status(InspectionStatus.CREATED)
                    .createdAt(Instant.now())
                    .build();

            InspectionJob save = inspectionJobRepository.save(job);

            return mapper.toDto(save);
        } catch (Exception ex) {
            throw new RuntimeException("Can't create inspection job" + ex.getMessage());
        }
    }

    public InspectionDto createFromEvent(JobCreatedEvent event) {
        try {
            InspectionJob job = InspectionJob.builder()
                    .houseId(event.getHouseId())
                    .type(InspectionType.valueOf(event.getType()))
                    .note(event.getType().equals("CHECK_IN")
                            ? "Kiểm tra nhà trước khi bàn giao"
                            : "Kiểm tra nhà khi kết thúc hợp đồng")
                    .status(InspectionStatus.CREATED)
                    .contractId(event.getReferenceId())
                    .createdAt(Instant.now())
                    .build();

            InspectionJob save = inspectionJobRepository.save(job);
            return mapper.toDto(save);
        } catch (Exception ex) {
            throw new RuntimeException("Can't create inspection job: " + ex.getMessage());
        }
    }

    @Override
    public PageResponse<InspectionDto> getAll(PageRequest request) {
        return cachedPageService.getOrLoad(PAGE_NS, request, new TypeReference<>() {
                },
                () -> loadPage(request)
        );
    }


    @Override
    public InspectionDto getInspectionById(UUID inspectionId) {
        try {
            InspectionJob job = inspectionJobRepository.findById(inspectionId)
                    .orElseThrow(() -> new RuntimeException("Id not found"));

            String staffName = null;
            String staffPhone = null;

            if (job.getAssignedStaffId() != null) {
                var user = userClientsGrpc.getUser(job.getAssignedStaffId().toString());
                staffName = user.getName();
                staffPhone = user.getPhoneNumber();
            }
            return new InspectionDto(
                    job.getId(),
                    job.getHouseId(),
                    job.getAssignedStaffId(),
                    staffName,
                    staffPhone,
                    job.getSlotId(),
                    job.getStatus(),
                    job.getType(),
                    job.getNote(),
                    job.getCreatedAt(),
                    job.getUpdatedAt()
            );
        } catch (Exception ex) {
            throw new RuntimeException("Can't get inspection by id" + ex.getMessage());
        }
    }

    @Override
    public InspectionDto updateStatus(UUID id, InspectionStatus newStatus) {
        try {

            InspectionJob job = inspectionJobRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Inspection not found"));

            InspectionStatus cur = job.getStatus();

            if (cur == InspectionStatus.SCHEDULED && newStatus == InspectionStatus.IN_PROGRESS) {
                job.setStatus(newStatus);
            } else if (cur == InspectionStatus.IN_PROGRESS && newStatus == InspectionStatus.DONE) {
                job.setStatus(newStatus);
            } else {
                throw new RuntimeException("Invalid status transition");
            }

            job.setUpdatedAt(Instant.now());

            InspectionJob saved = inspectionJobRepository.save(job);

            if (newStatus == InspectionStatus.DONE) {
                JobEvent event = JobEvent.builder()
                        .referenceId(job.getId())
                        .slotId(job.getSlotId())
                        .staffId(job.getAssignedStaffId())
                        .referenceType("INSPECTION")
                        .contractId(job.getContractId())
                        .action(JobAction.JOB_COMPLETED)
                        .build();

                jobEventProducer.publishJobCompleted(event);
            }

            return mapper.toDto(saved);

        } catch (Exception ex) {
            throw new RuntimeException("Can't update inspection status" + ex.getMessage());
        }
    }

    @Override
    public void markScheduled(JobEvent event) {
        InspectionJob job = inspectionJobRepository.findById(event.getReferenceId())
                .orElseThrow(() -> new RuntimeException("Job not found: " + event.getReferenceId()));

        if (job.getStatus() == InspectionStatus.SCHEDULED) {
            return;
        }

        job.setStatus(InspectionStatus.SCHEDULED);
        job.setAssignedStaffId(event.getStaffId());
        job.setSlotId(event.getSlotId());

        inspectionJobRepository.save(job);

        saveHistory(job, event);
    }

    private PageResponse<InspectionDto> loadPage(PageRequest request) {
        InspectionStatus statusFilter = request.<String>filterValue("status")
                .map(s -> {
                    try {
                        return InspectionStatus.valueOf(s.toUpperCase().trim());
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .orElse(null);

        InspectionType typeFilter = request.<String>filterValue("type")
                .map(t -> {
                    try {
                        return InspectionType.valueOf(t.toUpperCase().trim());
                    } catch (Exception e) {
                        return null;
                    }
                })
                .orElse(null);

        String statusesRaw = request.<String>filterValue("statuses").orElse(null);

        String houseIdRaw = request.<String>filterValue("houseId").orElse(null);
        UUID houseIdFilter = houseIdRaw != null ? UUID.fromString(houseIdRaw) : null;

        var spec = SpecificationBuilder.<InspectionJob>create()
                .keywordLike(request.keyword(), "note")
                .enumEq("status", statusFilter)
                .enumInRaw("status", statusesRaw, InspectionStatus.class)
                .eq("houseId", houseIdFilter)
                .enumEq("type", typeFilter)
                .build();
        var pageable = SpringPageConverter.toPageable(request);
        Page<InspectionJob> page = inspectionJobRepository.findAll(spec, pageable);
        return SpringPageConverter.fromPage(page, mapper::toDto);
    }

    private void saveHistory(InspectionJob job, JobEvent event) {
        MaintenanceJobHistory history = new MaintenanceJobHistory();

        history.setJobId(job.getId());
        history.setAction(event.getAction().name());
        history.setActorId(event.getStaffId());
        history.setCreatedAt(Instant.now());

        historyRepository.save(history);
    }
}
