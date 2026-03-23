package com.isums.maintainservice.services;

import com.isums.maintainservice.domains.dtos.MaintainJobDTO.MaintenanceJobDto;
import com.isums.maintainservice.domains.entities.MaintenanceJob;
import com.isums.maintainservice.domains.entities.MaintenanceJobHistory;
import com.isums.maintainservice.domains.entities.PeriodicInspectionPlan;
import com.isums.maintainservice.domains.entities.PlanHouse;
import com.isums.maintainservice.domains.enums.JobStatus;
import com.isums.maintainservice.domains.events.JobEvent;
import com.isums.maintainservice.infrastructures.abstracts.MaintenanceJobService;
import com.isums.maintainservice.infrastructures.gRpc.UserClientsGrpc;
import com.isums.maintainservice.infrastructures.mappers.MaintenanceMapper;
import com.isums.maintainservice.infrastructures.repositories.MaintenanceJobHistoryRepository;
import com.isums.maintainservice.infrastructures.repositories.MaintenanceJobRepository;
import com.isums.maintainservice.infrastructures.repositories.PeriodicInspectionPlanRepository;
import com.isums.maintainservice.infrastructures.repositories.PlanHouseRepository;
import com.isums.userservice.grpc.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MaintenanceJobServiceImpl implements MaintenanceJobService {
    private final PeriodicInspectionPlanRepository periodicInspectionPlanRepository;
    private final PlanHouseRepository planHouseRepository;
    private final MaintenanceMapper maintenanceMapper;
    private final MaintenanceJobRepository maintenanceJobRepository;
    private final MaintenanceJobHistoryRepository historyRepository;
    private final UserClientsGrpc userClientsGrpc;


    @Override
    public List<MaintenanceJobDto> generateMaintainJobs() {
        try{
            LocalDate today = LocalDate.now();

            List<PeriodicInspectionPlan> plans = periodicInspectionPlanRepository.findByNextRunAtLessThanEqualAndIsActiveTrue(today);

            List<MaintenanceJob> jobs = new ArrayList<>();

            for(PeriodicInspectionPlan plan : plans){
                LocalDate periodStart = plan.getNextRunAt();

                List<PlanHouse> houses = planHouseRepository.findByPlanId(plan.getId());
                for(PlanHouse house : houses){
                    boolean exist = maintenanceJobRepository.existsByPlanIdAndHouseIdAndPeriodStartDate(plan.getId(),house.getHouseId(),periodStart);

                    if(exist){
                        continue;
                    }
                    MaintenanceJob job = MaintenanceJob.builder()
                            .planId(plan.getId())
                            .houseId(house.getHouseId())
                            .periodStartDate(plan.getNextRunAt())
                            .dueDate(plan.getNextRunAt().atStartOfDay().plusDays(7).toInstant(ZoneOffset.UTC))
                            .status(JobStatus.CREATED)
                            .createdAt(Instant.now())
                            .build();

                    jobs.add(job);
                }
                plan.setNextRunAt(calculateNextRun(plan));
            }
            maintenanceJobRepository.saveAll(jobs);

            return maintenanceMapper.jobs(jobs);


        } catch (Exception ex) {
            throw new RuntimeException("Can't generate maintenance jobs " + ex.getMessage());
        }
    }

    @Override
    public List<MaintenanceJobDto> getAllJobs() {
        try{
            List<MaintenanceJob> jobs = maintenanceJobRepository.findAllByOrderByCreatedAtDesc();
            return maintenanceMapper.jobs(jobs);

        } catch (Exception ex) {
            throw new RuntimeException("Can't get all jobs" + ex.getMessage());
        }
    }

    @Override
    public MaintenanceJobDto getJobById(UUID jobId) {
        try{
            MaintenanceJob job = maintenanceJobRepository.findById(jobId)
                    .orElseThrow(() -> new RuntimeException("Id not found"));
            return maintenanceMapper.job(job);
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

    @Override
    public List<MaintenanceJobDto> getJobsByStatus(JobStatus status) {
        List<MaintenanceJob> jobs = maintenanceJobRepository.findByStatus(status);
        return maintenanceMapper.jobs(jobs);
    }

    @Override
    public void markScheduled(JobEvent event) {
        MaintenanceJob job = maintenanceJobRepository.findById(event.getReferenceId())
                .orElseThrow();

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
                .orElseThrow();

        job.setStatus(JobStatus.SCHEDULED);
        job.setAssignedStaffId(event.getStaffId());
        job.setSlotId(event.getSlotId());

        maintenanceJobRepository.save(job);

        saveHistory(job, event);
    }

    @Override
    public void markNeedReschedule(JobEvent event) {
        MaintenanceJob job = maintenanceJobRepository.findById(event.getReferenceId())
                .orElseThrow();

        job.setStatus(JobStatus.NEED_RESCHEDULE);

        maintenanceJobRepository.save(job);
        saveHistory(job, event);
    }

    @Override
    public MaintenanceJobDto updateJobStatus(UUID jobId, JobStatus newStatus) {
        try{
            MaintenanceJob job = maintenanceJobRepository.findById(jobId)
                    .orElseThrow(() -> new RuntimeException("Job not found"));

            JobStatus cur = job.getStatus();

            if(cur == JobStatus.SCHEDULED && newStatus == JobStatus.IN_PROGRESS){
                job.setStatus(JobStatus.IN_PROGRESS);
            }else if(cur == JobStatus.IN_PROGRESS && newStatus == JobStatus.COMPLETED){
                job.setStatus(JobStatus.COMPLETED);
            }else{
                throw new RuntimeException("Invalid status transition");
            }

            MaintenanceJob save = maintenanceJobRepository.save(job);

            saveLog(save,newStatus);

            return maintenanceMapper.job(save);

        } catch (Exception ex) {
            throw new RuntimeException(" Can't update job status " + ex.getMessage());
        }
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

    private LocalDate calculateNextRun(PeriodicInspectionPlan plan){
        return switch (plan.getFrequencyType()) {
            case MONTHLY -> plan.getNextRunAt().plusMonths(plan.getFrequencyValue());
            case QUARTERLY -> plan.getNextRunAt().plusMonths((3L * plan.getFrequencyValue()));
            case YEARLY -> plan.getNextRunAt().plusYears(plan.getFrequencyValue());
            default -> throw new RuntimeException("Invalid frequency type");
        };
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
