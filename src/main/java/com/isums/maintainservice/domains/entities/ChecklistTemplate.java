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
@Table(name = "checklist_templates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChecklistTemplate {

    @Id
    @UuidGenerator
    @GeneratedValue
    private UUID id;

    @Column(unique = true)
    private String code;

    private String label;

    private Instant createdAt;
}
