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
@Table(name = "maintenance_execution_images")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceImage {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "object_key", nullable = false)
    private String objectKey;

    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_id")
    private MaintenanceExecution execution;
}