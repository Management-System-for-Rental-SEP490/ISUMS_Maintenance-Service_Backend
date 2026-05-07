package com.isums.maintainservice.infrastructures.mappers;

import com.isums.maintainservice.domains.dtos.InspectionDto;
import com.isums.maintainservice.domains.entities.InspectionJob;
import com.isums.maintainservice.services.S3ServiceImpl;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Mapper(componentModel = "spring")
public abstract class InspectionMapper {

    @Autowired
    protected S3ServiceImpl s3Service;

    @Mapping(target = "staffName", ignore = true)
    @Mapping(target = "staffPhone", ignore = true)
    @Mapping(target = "note", expression = "java(resolveNote(job))")
    @Mapping(target = "housePhotoUrls", expression = "java(resolveHousePhotoUrls(job))")
    public abstract InspectionDto toDto(InspectionJob job);

    public abstract List<InspectionDto> toDtos(List<InspectionJob> jobs);

    protected String resolveNote(InspectionJob job) {
        if (job == null) return null;
        if (job.getNoteTranslations() != null) {
            String resolved = job.getNoteTranslations().resolve();
            if (resolved != null && !resolved.isBlank()) return resolved;
        }
        return job.getNote();
    }

    protected List<String> resolveHousePhotoUrls(InspectionJob job) {
        if (job == null || job.getHousePhotoKeys() == null || job.getHousePhotoKeys().isEmpty()) {
            return Collections.emptyList();
        }
        return job.getHousePhotoKeys().parallelStream()
                .map(s3Service::getImageUrl)
                .filter(Objects::nonNull)
                .toList();
    }
}
