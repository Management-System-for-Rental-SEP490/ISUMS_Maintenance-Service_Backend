package com.isums.maintainservice.domains.entities;

import com.isums.maintainservice.domains.enums.InspectionStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inspection_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InspectionJob {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    private UUID houseId;

    private UUID assignedStaffId;

    private UUID slotId;

    private String note;

    @Enumerated(EnumType.STRING)
    private InspectionStatus status;

    private Instant createdAt;

    private Instant updatedAt;
}
