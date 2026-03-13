package com.isums.maintainservice.domains.entities;

import com.isums.maintainservice.domains.enums.ChecklistResult;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "maintenance_checklist_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceChecklistItem {

    @Id
    @UuidGenerator
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    private ChecklistResult result;

    @Column(columnDefinition = "text")
    private String notes;

    private Instant createdAt;

    @Column(name = "job_id")
    private UUID jobId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checklist_template_id")
    private ChecklistTemplate template;
}
