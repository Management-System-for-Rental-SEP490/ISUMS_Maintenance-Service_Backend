package com.isums.maintainservice.domains.entities;

import com.isums.maintainservice.domains.enums.JobStatus;
import com.isums.maintainservice.domains.enums.JobType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "maintenance_jobs",
        indexes = {
                @Index(name = "idx_job_house", columnList = "house_id"),
                @Index(name = "idx_job_plan", columnList = "plan_id"),
                @Index(name = "idx_job_status", columnList = "status")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_job_plan_house_period",
                        columnNames = {"plan_id","house_id","period_start_date"}
                )
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceJob {

    @Id
    @UuidGenerator
    @GeneratedValue
    private UUID id;

    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "house_id", nullable = false)
    private UUID houseId;

    @Enumerated(EnumType.STRING)
    private JobType type;

    @Enumerated(EnumType.STRING)
    private JobStatus status;

    @Column(name = "assigned_staff_id")
    private UUID assignedStaffId;

    @Column(name = "slot_id")
    private UUID slotId;

    @Column(name = "period_start_date", nullable = false)
    private LocalDate periodStartDate;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @OneToMany(mappedBy = "job", fetch = FetchType.LAZY)
    private List<MaintenanceExecution> executions;
}