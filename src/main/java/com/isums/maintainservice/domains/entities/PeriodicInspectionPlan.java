package com.isums.maintainservice.domains.entities;

import com.isums.maintainservice.domains.enums.FrequencyType;
import common.i18n.TranslationMap;
import common.i18n.TranslationMapConverter;
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
import java.util.UUID;

@Entity
@Table(name = "periodic_inspection_plans",
        indexes = {
                @Index(name = "idx_plan_next_run", columnList = "next_run_at"),
                @Index(name = "idx_plan_active", columnList = "is_active")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PeriodicInspectionPlan {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "manager_id", nullable = false)
    private UUID managerId;

    @Column(nullable = false)
    private String name;

    @Column(name = "name_translations", columnDefinition = "text")
    @Convert(converter = TranslationMapConverter.class)
    private TranslationMap nameTranslations;

    @Column(name = "source_language", length = 8)
    private String sourceLanguage;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency_type", nullable = false)
    private FrequencyType frequencyType;

    @Column(name = "frequency_value", nullable = false)
    private Integer frequencyValue;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "next_run_at", nullable = false)
    private LocalDate nextRunAt;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}