package com.isums.maintainservice.services;

import com.isums.maintainservice.domains.dtos.MaintainJobDTO.MaintenanceJobDto;
import com.isums.maintainservice.domains.entities.MaintenanceJob;
import com.isums.maintainservice.domains.entities.PeriodicInspectionPlan;
import com.isums.maintainservice.domains.entities.PlanHouse;
import com.isums.maintainservice.domains.enums.JobStatus;
import com.isums.maintainservice.infrastructures.abstracts.MaintenanceJobService;
import com.isums.maintainservice.infrastructures.mappers.MaintenanceMapper;
import com.isums.maintainservice.infrastructures.repositories.MaintenanceJobRepository;
import com.isums.maintainservice.infrastructures.repositories.PeriodicInspectionPlanRepository;
import com.isums.maintainservice.infrastructures.repositories.PlanHouseRepository;
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

    @Override
    public List<MaintenanceJobDto> generateMaintainJobs() {
        try{
            LocalDate today = LocalDate.now();

            List<PeriodicInspectionPlan> plans = periodicInspectionPlanRepository.findByNextRunAtLessThanEqualAndIsActiveTrue(today);

            List<MaintenanceJob> jobs = new ArrayList<>();

            for(PeriodicInspectionPlan plan : plans){
                List<PlanHouse> houses = planHouseRepository.findByPlanId(plan.getId());
                for(PlanHouse house : houses){
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

    private LocalDate calculateNextRun(PeriodicInspectionPlan plan){
        switch (plan.getFrequencyType()){
            case MONTHLY :
                return plan.getNextRunAt().plusMonths(plan.getFrequencyValue());
            case QUARTERLY:
                return plan.getNextRunAt().plusMonths((3L * plan.getFrequencyValue()));
            case YEARLY:
                return plan.getNextRunAt().plusYears(plan.getFrequencyValue());

            default:
                throw new RuntimeException("Invalid frequency type");
        }
    }
}
