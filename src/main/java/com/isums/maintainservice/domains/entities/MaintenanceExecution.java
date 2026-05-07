package com.isums.maintainservice.domains.entities;

import common.i18n.TranslationMap;
import common.i18n.TranslationMapConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "maintenance_executions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaintenanceExecution {

    @Id
    @UuidGenerator
    @GeneratedValue
    private UUID id;

    @Column(name = "house_id", nullable = false)
    private UUID houseId;

    @Column(name = "asset_id")
    private UUID assetId;

    @Column(name = "staff_id", nullable = false)
    private UUID staffId;

    @Column(name = "condition_score")
    private Integer conditionScore;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "notes_translations", columnDefinition = "text")
    @Convert(converter = TranslationMapConverter.class)
    private TranslationMap notesTranslations;

    @Column(name = "source_language", length = 8)
    private String sourceLanguage;

    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    private MaintenanceJob job;

    @OneToMany(mappedBy = "execution", fetch = FetchType.LAZY)
    private List<MaintenanceImage> images;
}
