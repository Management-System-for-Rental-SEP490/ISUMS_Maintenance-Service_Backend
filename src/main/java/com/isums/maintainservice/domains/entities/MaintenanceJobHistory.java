package com.isums.maintainservice.domains.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "maintenance_job_histories")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceJobHistory {

    @Id
    @UuidGenerator
    @GeneratedValue
    private UUID id;

    @Column(name = "job_id")
    private UUID jobId;

    private String action;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(columnDefinition = "text")
    private String message;

    private Instant createdAt;
}