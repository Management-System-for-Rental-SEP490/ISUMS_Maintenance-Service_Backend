package com.isums.maintainservice.domains.entities;

import com.isums.maintainservice.domains.enums.InspectionStatus;
import com.isums.maintainservice.domains.enums.InspectionType;
import common.i18n.TranslationMap;
import common.i18n.TranslationMapConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

    @Column(name = "note_translations", columnDefinition = "text")
    @Convert(converter = TranslationMapConverter.class)
    private TranslationMap noteTranslations;

    @Column(name = "source_language", length = 8)
    private String sourceLanguage;

    @Column(name = "contract_id")
    private UUID contractId;

    @Enumerated(EnumType.STRING)
    private InspectionStatus status;

    @Enumerated(EnumType.STRING)
    private InspectionType type;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "inspection_house_photos",
            joinColumns = @JoinColumn(name = "inspection_id")
    )
    @Column(name = "object_key", nullable = false)
    @Builder.Default
    private List<String> housePhotoKeys = new ArrayList<>();

    private Instant createdAt;

    private Instant updatedAt;
}
