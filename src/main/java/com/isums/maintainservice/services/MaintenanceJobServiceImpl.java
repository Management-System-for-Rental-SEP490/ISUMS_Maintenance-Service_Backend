package com.isums.maintainservice.services;

import com.isums.maintainservice.domains.dtos.MaintainJobDTO.MaintenanceJobDto;
import com.isums.maintainservice.domains.entities.MaintenanceJob;
import com.isums.maintainservice.domains.entities.MaintenanceJobHistory;
import com.isums.maintainservice.domains.entities.PeriodicInspectionPlan;
import com.isums.maintainservice.domains.entities.PlanHouse;
import com.isums.maintainservice.domains.enums.JobAction;
import com.isums.maintainservice.domains.enums.JobStatus;
import com.isums.maintainservice.domains.events.JobEvent;
import com.isums.maintainservice.domains.events.SlotEvent;
import com.isums.maintainservice.infrastructures.abstracts.MaintenanceJobService;
import com.isums.maintainservice.infrastructures.gRpc.UserClientsGrpc;
import com.isums.maintainservice.infrastructures.kafka.JobEventProducer;
import com.isums.maintainservice.infrastructures.kafka.SlotProducer;
import com.isums.maintainservice.infrastructures.mappers.MaintenanceMapper;
import com.isums.maintainservice.infrastructures.repositories.MaintenanceJobHistoryRepository;
import com.isums.maintainservice.infrastructures.repositories.MaintenanceJobRepository;
import com.isums.maintainservice.infrastructures.repositories.PeriodicInspectionPlanRepository;
import com.isums.maintainservice.infrastructures.repositories.PlanHouseRepository;
import com.isums.maintainservice.exceptions.BadRequestException;
import com.isums.maintainservice.exceptions.NotFoundException;
import com.isums.userservice.grpc.UserResponse;
import common.paginations.cache.CachedPageService;
import common.paginations.converters.SpringPageConverter;
import common.paginations.dtos.PageRequest;
import common.paginations.dtos.PageResponse;
import common.paginations.specifications.SpecificationBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MaintenanceJobServiceImpl implements MaintenanceJobService {
    private final PeriodicInspectionPlanRepository periodicInspectionPlanRepository;
    private final PlanHouseRepository planHouseRepository;
    private final MaintenanceMapper maintenanceMapper;
    private final MaintenanceJobRepository maintenanceJobRepository;
    private final MaintenanceJobHistoryRepository historyRepository;
    private final UserClientsGrpc userClientsGrpc;
    private final JobEventProducer jobEventProducer;
    private final CachedPageService cachedPageService;

    private static final String PAGE_NS = "maintenances";
    private static final Duration PAGE_TTL = Duration.ofMinutes(60);


    @Override
    @Transactional
    public List<MaintenanceJobDto> generateMaintainJobs() {
        try{
            LocalDate today = LocalDate.now();

            List<PeriodicInspectionPlan> plans = periodicInspectionPlanRepository.findByNextRunAtLessThanEqualAndIsActiveTrue(today);

            List<MaintenanceJob> jobs = new ArrayList<>();

            for(PeriodicInspectionPlan plan : plans){
                LocalDate periodStart = plan.getNextRunAt();

                List<PlanHouse> houses = planHouseRepository.findByPlanId(plan.getId());

                if (houses == null || houses.isEmpty()) {
                    continue;
                }

                List<UUID> houseIds = houses.stream()
                        .map(PlanHouse::getHouseId)
                        .toList();

                List<UUID> existingHouseIds = maintenanceJobRepository
                        .findExistingHouseIds(plan.getId(), periodStart, houseIds);

                Set<UUID> existingSet = new HashSet<>(existingHouseIds);

                int beforeSize = jobs.size();

                for(PlanHouse house : houses){
                    if (existingSet.contains(house.getHouseId())) {
                        continue;
                    }

                    MaintenanceJob job = MaintenanceJob.builder()
                            .planId(plan.getId())
                            .houseId(house.getHouseId())
                            .periodStartDate(plan.getNextRunAt())
                            .status(JobStatus.CREATED)
                            .createdAt(Instant.now())
                            .build();

                    jobs.add(job);
                }
                if (jobs.size() > beforeSize) {
                    plan.setNextRunAt(calculateNextRun(plan));
                }
            }

            if (jobs.isEmpty()) {
                return List.of();
            }

            maintenanceJobRepository.saveAll(jobs);

            periodicInspectionPlanRepository.saveAll(plans);

            if (!jobs.isEmpty()) {
                for (MaintenanceJob job : jobs) {
                    JobEvent event = JobEvent.builder()
                            .referenceId(job.getId())
                            .houseId(job.getHouseId())
                            .referenceType("MAINTENANCE")
                            .action(JobAction.JOB_CREATED)
                            .build();

                    jobEventProducer.publishJobCreated(event);
                }
            }

            return maintenanceMapper.jobs(jobs);


        } catch (Exception ex) {
            throw new RuntimeException("Can't generate maintenance jobs " + ex.getMessage());
        }
    }

    @Override
    public PageResponse<MaintenanceJobDto> getAll(PageRequest request) {
        return cachedPageService.getOrLoad(PAGE_NS, request, new TypeReference<>() {
                },
                () -> loadPage(request)
        );
    }

    @Override
    public MaintenanceJobDto getJobById(UUID jobId) {
        try{
            MaintenanceJob job = maintenanceJobRepository.findById(jobId)
                    .orElseThrow(() -> new RuntimeException("Id not found"));

            String staffName = null;
            String staffPhone = null;

            if (job.getAssignedStaffId() != null) {
                var user = userClientsGrpc.getUser(job.getAssignedStaffId().toString());
                staffName = user.getName();
                staffPhone = user.getPhoneNumber();
            }

            return new MaintenanceJobDto(
                    job.getId(),
                    job.getPlanId(),
                    job.getHouseId(),
                    job.getAssignedStaffId(),
                    staffName,
                    staffPhone,
                    job.getPeriodStartDate(),
                    job.getStatus()
            );

        }catch (Exception ex){
            throw new RuntimeException("Can't get job" + ex.getMessage());
        }
    }

    @Override
    public List<MaintenanceJobDto> getJobByHouseId(UUID houseId) {
        try {
            List<MaintenanceJob> jobs = maintenanceJobRepository.findByHouseIdOrderByCreatedAtDesc(houseId);
            return maintenanceMapper.jobs(jobs);
        } catch (Exception ex) {
            throw new RuntimeException("Can't get job" + ex.getMessage());
        }
    }

//    @Override
//    public List<MaintenanceJobDto> getJobsByStatus(JobStatus status) {
//        List<MaintenanceJob> jobs = maintenanceJobRepository.findByStatus(status);
//        return maintenanceMapper.jobs(jobs);
//    }

    @Override
    public void markScheduled(JobEvent event) {
        MaintenanceJob job = maintenanceJobRepository.findById(event.getReferenceId())
                .orElseThrow(() -> new RuntimeException("Job not found: " + event.getReferenceId()));

        if(job.getStatus() == JobStatus.SCHEDULED){
            return;
        }

        job.setStatus(JobStatus.SCHEDULED);
        job.setAssignedStaffId(event.getStaffId());
        job.setSlotId(event.getSlotId());

        maintenanceJobRepository.save(job);

        saveHistory(job, event);

    }

    @Override
    public void markRescheduled(JobEvent event) {
        MaintenanceJob job = maintenanceJobRepository.findById(event.getReferenceId())
                .orElseThrow(() -> new RuntimeException("Job not found: " + event.getReferenceId()));

        job.setStatus(JobStatus.SCHEDULED);
        job.setAssignedStaffId(event.getStaffId());
        job.setSlotId(event.getSlotId());

        maintenanceJobRepository.save(job);

        saveHistory(job, event);
    }

    @Override
    public void markNeedReschedule(JobEvent event) {
        MaintenanceJob job = maintenanceJobRepository.findById(event.getReferenceId())
                .orElseThrow(() -> new RuntimeException("Job not found: " + event.getReferenceId()));

        job.setStatus(JobStatus.NEED_RESCHEDULE);

        maintenanceJobRepository.save(job);
        saveHistory(job, event);
    }

    @Override
    public void markSlot(JobEvent event) {
        MaintenanceJob job = maintenanceJobRepository.findById((event.getReferenceId()))
                .orElseThrow(() -> new RuntimeException("Job not found"));

        if (job.getSlotId() != null) {
            return;
        }
        job.setSlotId(event.getSlotId());

        maintenanceJobRepository.save(job);
        saveHistory(job,event);
    }

    @Override
    public MaintenanceJobDto updateJobStatus(UUID jobId, JobStatus newStatus) {
        MaintenanceJob job = maintenanceJobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Job not found"));

        JobStatus cur = job.getStatus();

        if (cur == JobStatus.SCHEDULED && newStatus == JobStatus.IN_PROGRESS) {
            job.setStatus(JobStatus.IN_PROGRESS);
        } else if (cur == JobStatus.IN_PROGRESS && newStatus == JobStatus.COMPLETED) {
            job.setStatus(JobStatus.COMPLETED);
        } else {
            throw new BadRequestException("Invalid status transition");
        }

        MaintenanceJob save = maintenanceJobRepository.save(job);

        if (newStatus == JobStatus.COMPLETED) {
            JobEvent event = JobEvent.builder()
                    .referenceId(jobId)
                    .slotId(job.getSlotId())
                    .staffId(job.getAssignedStaffId())
                    .referenceType("MAINTENANCE")
                    .action(JobAction.JOB_COMPLETED)
                    .build();

            jobEventProducer.publishJobCompleted(event);
        }

        saveLog(save, newStatus);
        cachedPageService.evictAll(PAGE_NS);
        return maintenanceMapper.job(save);
    }

    @Override
    public List<MaintenanceJobDto> getJobsByStaffId(String staffId) {
        try{
            UserResponse user = userClientsGrpc.getUserIdAndRoleByKeyCloakId(staffId);
            List<MaintenanceJob> jobs = maintenanceJobRepository.findByAssignedStaffIdOrderByCreatedAtDesc(UUID.fromString(user.getId()));
            return maintenanceMapper.jobs(jobs);
        } catch (Exception ex) {
            throw new RuntimeException("Can't get jobs for staff " + ex.getMessage());

        }
    }

    @Override
    public List<MaintenanceJobDto> getJobsByPlanID(UUID planId) {
        try{
            List<MaintenanceJob> jobs = maintenanceJobRepository.findByPlanId(planId);
            return maintenanceMapper.jobs(jobs);
        } catch (Exception ex){
            throw new RuntimeException("Can't get job by planId " + ex.getMessage());
        }
    }

    @Transactional
    @Override
    public List<MaintenanceJobDto> generateByPlan(UUID planId) {

        PeriodicInspectionPlan plan = periodicInspectionPlanRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Plan not found"));

        List<PlanHouse> houses = planHouseRepository.findByPlanId(planId);

        if (houses.isEmpty()) {
            throw new BadRequestException("Plan has no houses");
        }

        LocalDate periodStart = plan.getNextRunAt();

        List<UUID> houseIds = houses.stream()
                .map(PlanHouse::getHouseId)
                .toList();

        List<UUID> existingHouseIds = maintenanceJobRepository
                .findExistingHouseIds(planId, periodStart, houseIds);

        Set<UUID> existingSet = new HashSet<>(existingHouseIds);

        List<MaintenanceJob> jobs = new ArrayList<>();

        for (PlanHouse house : houses) {

            if (existingSet.contains(house.getHouseId())) {
                continue;
            }

            MaintenanceJob job = MaintenanceJob.builder()
                    .planId(planId)
                    .houseId(house.getHouseId())
                    .periodStartDate(periodStart)
                    .status(JobStatus.CREATED)
                    .createdAt(Instant.now())
                    .build();

            jobs.add(job);
        }

        if (jobs.isEmpty()) {
            throw new BadRequestException("All houses already have jobs for this period");
        }

        maintenanceJobRepository.saveAll(jobs);

//        plan.setNextRunAt(calculateNextRun(plan));
//        periodicInspectionPlanRepository.save(plan);

        for (MaintenanceJob job : jobs) {
            JobEvent event = JobEvent.builder()
                    .referenceId(job.getId())
                    .houseId(job.getHouseId())
                    .referenceType("MAINTENANCE")
                    .action(JobAction.JOB_CREATED)
                    .build();

            jobEventProducer.publishJobCreated(event);
        }

        return maintenanceMapper.jobs(jobs);
    }



    private LocalDate calculateNextRun(PeriodicInspectionPlan plan){
        return switch (plan.getFrequencyType()) {
            case MONTHLY -> plan.getNextRunAt().plusMonths(plan.getFrequencyValue());
            case QUARTERLY -> plan.getNextRunAt().plusMonths((3L * plan.getFrequencyValue()));
            case YEARLY -> plan.getNextRunAt().plusYears(plan.getFrequencyValue());
            default -> throw new RuntimeException("Invalid frequency type");
        };
    }

    private PageResponse<MaintenanceJobDto> loadPage(PageRequest request) {
        JobStatus statusFilter = request.<String>filterValue("status")
                .map(s -> {
                    try {
                        return JobStatus.valueOf(s.toUpperCase().trim());
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .orElse(null);

        String statusesRaw = request.<String>filterValue("statuses").orElse(null);

        String houseIdRaw = request.<String>filterValue("houseId").orElse(null);
        UUID houseIdFilter = houseIdRaw != null ? UUID.fromString(houseIdRaw) : null;

        var spec = SpecificationBuilder.<MaintenanceJob>create()
                .keywordLike(request.keyword(), "note")
                .enumEq("status", statusFilter)
                .enumInRaw("status", statusesRaw, JobStatus.class)
                .eq("houseId", houseIdFilter)
                .build();
        var pageable = SpringPageConverter.toPageable(request);
        Page<MaintenanceJob> page = maintenanceJobRepository.findAll(spec, pageable);
        return SpringPageConverter.fromPage(page, maintenanceMapper::job);
    }

    private void saveHistory(MaintenanceJob job,JobEvent event){
        MaintenanceJobHistory history = new MaintenanceJobHistory();

        history.setJobId(job.getId());
        history.setAction(event.getAction().name());
        history.setActorId(event.getStaffId());
        history.setCreatedAt(Instant.now());

        historyRepository.save(history);
    }

    private void saveLog(MaintenanceJob job, JobStatus status){

        MaintenanceJobHistory history = new MaintenanceJobHistory();

        history.setJobId(job.getId());
        history.setAction("JOB_" + status.name());
        history.setActorId(job.getAssignedStaffId());
        history.setCreatedAt(Instant.now());

        historyRepository.save(history);
    }
}
