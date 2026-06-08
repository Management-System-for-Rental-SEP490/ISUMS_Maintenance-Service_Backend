package com.isums.maintainservice.services;

import com.isums.maintainservice.domains.dtos.CreateInspectionRequest;
import com.isums.maintainservice.domains.dtos.InspectionDto;
import com.isums.maintainservice.domains.entities.InspectionJob;
import com.isums.maintainservice.domains.entities.MaintenanceJob;
import com.isums.maintainservice.domains.entities.MaintenanceJobHistory;
import com.isums.maintainservice.domains.enums.InspectionStatus;
import com.isums.maintainservice.domains.enums.InspectionType;
import com.isums.maintainservice.domains.enums.JobAction;
import com.isums.maintainservice.domains.events.JobCreatedEvent;
import com.isums.maintainservice.domains.events.JobEvent;
import com.isums.maintainservice.exceptions.BadRequestException;
import com.isums.maintainservice.exceptions.NotFoundException;
import com.isums.maintainservice.infrastructures.i18n.MaintenanceMessageKeys;
import com.isums.maintainservice.infrastructures.abstracts.InspectionJobService;
import com.isums.maintainservice.domains.dtos.IssueQuoteDto;
import com.isums.maintainservice.infrastructures.gRpc.HouseClientsGrpc;
import com.isums.maintainservice.infrastructures.gRpc.QuoteClientsGrpc;
import com.isums.maintainservice.infrastructures.gRpc.UserClientsGrpc;
import com.isums.houseservice.grpc.HouseResponse;
import com.isums.maintainservice.infrastructures.kafka.JobEventProducer;
import com.isums.maintainservice.infrastructures.mappers.InspectionMapper;
import com.isums.maintainservice.infrastructures.repositories.InspectionJobRepository;
import com.isums.maintainservice.infrastructures.repositories.MaintenanceJobHistoryRepository;
import common.i18n.TranslationMap;
import common.paginations.cache.CachedPageService;
import common.paginations.converters.SpringPageConverter;
import common.paginations.dtos.PageRequest;
import common.paginations.dtos.PageResponse;
import common.paginations.specifications.SpecificationBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InspectionJobServiceImpl implements InspectionJobService {
    private final InspectionJobRepository inspectionJobRepository;
    private final InspectionMapper mapper;
    private final JobEventProducer jobEventProducer;
    private final MaintenanceJobHistoryRepository historyRepository;
    private final UserClientsGrpc userClientsGrpc;
    private final QuoteClientsGrpc quoteClientsGrpc;
    private final HouseClientsGrpc houseClientsGrpc;
    private final CachedPageService cachedPageService;
    private final TranslationAutoFillService translationAutoFillService;
    private final S3ServiceImpl s3Service;

    private static final String PAGE_NS = "inspections";
    private static final Duration PAGE_TTL = Duration.ofMinutes(60);
    private static final String DEFAULT_LANGUAGE = "vi";

    @Override
    public InspectionDto create(String creatorKeycloakId, CreateInspectionRequest request) {
        try {
            String sourceLanguage = resolveUserLanguageFromKeycloak(creatorKeycloakId);
            TranslationMap noteTranslations = translationAutoFillService.complete(request.note(), sourceLanguage);

            InspectionJob job = InspectionJob.builder()
                    .houseId(request.houseId())
                    .type(request.type())
                    .note(request.note())
                    .noteTranslations(noteTranslations)
                    .sourceLanguage(sourceLanguage)
                    .status(InspectionStatus.CREATED)
                    .createdAt(Instant.now())
                    .build();

            InspectionJob save = inspectionJobRepository.save(job);

            JobEvent assignEvent = JobEvent.builder()
                    .referenceId(job.getId())
                    .houseId(job.getHouseId())
                    .referenceType("INSPECTION")
                    .action(JobAction.JOB_CREATED)
                    .build();
            jobEventProducer.publishJobCreated(assignEvent);
            cachedPageService.evictAll(PAGE_NS);
            return mapper.toDto(save);
        } catch (Exception ex) {
            throw new RuntimeException(MaintenanceMessageKeys.CANNOT_CREATE_INSPECTION + ": " + ex);
        }
    }

    public InspectionDto createFromEvent(JobCreatedEvent event) {
        try {
            String noteVi = event.getType().equals("CHECK_IN")
                    ? "Kiểm tra bàn giao nhà trước khi khách vào ở"
                    : "Kiểm tra trả nhà khi kết thúc hợp đồng";
            TranslationMap noteTranslations = translationAutoFillService.complete(noteVi, DEFAULT_LANGUAGE);

            InspectionJob job = InspectionJob.builder()
                    .houseId(event.getHouseId())
                    .type(InspectionType.valueOf(event.getType()))
                    .note(noteVi)
                    .noteTranslations(noteTranslations)
                    .sourceLanguage(DEFAULT_LANGUAGE)
                    .status(InspectionStatus.CREATED)
                    .contractId(event.getReferenceId())
                    .createdAt(Instant.now())
                    .build();

            InspectionJob save = inspectionJobRepository.save(job);
            JobEvent assignEvent = JobEvent.builder()
                    .referenceId(job.getId())
                    .houseId(job.getHouseId())
                    .referenceType("INSPECTION")
                    .action(JobAction.JOB_CREATED)
                    .build();
            jobEventProducer.publishJobCreated(assignEvent);
            cachedPageService.evictAll(PAGE_NS);
            return mapper.toDto(save);
        } catch (Exception ex) {
            throw new RuntimeException(MaintenanceMessageKeys.CANNOT_CREATE_INSPECTION + ": " + ex);
        }
    }

    @Override
    public PageResponse<InspectionDto> getAll(PageRequest request) {
        return cachedPageService.getOrLoad(PAGE_NS + ":" + TranslationMap.currentLanguage(), request, new TypeReference<>() {
                },
                () -> loadPage(request)
        );
    }

    @Override
    public InspectionDto getInspectionById(UUID inspectionId) {
        try {
            InspectionJob job = inspectionJobRepository.findById(inspectionId)
                    .orElseThrow(() -> new NotFoundException(MaintenanceMessageKeys.INSPECTION_NOT_FOUND));

            String staffName = null;
            String staffPhone = null;

            if (job.getAssignedStaffId() != null) {
                var user = userClientsGrpc.getUser(job.getAssignedStaffId().toString());
                staffName = user.getName();
                staffPhone = user.getPhoneNumber();
            }
            IssueQuoteDto quote = QuoteAdapter.toDto(
                    quoteClientsGrpc.getLatestByReference(job.getId(), "INSPECTION"));
            return new InspectionDto(
                    job.getId(),
                    job.getHouseId(),
                    null,
                    null,
                    job.getContractId(),
                    job.getAssignedStaffId(),
                    staffName,
                    staffPhone,
                    job.getSlotId(),
                    job.getStatus(),
                    job.getType(),
                    resolveNote(job),
                    resolveHousePhotoUrls(job),
                    job.getCreatedAt(),
                    job.getUpdatedAt(),
                    quote
            );
        } catch (NotFoundException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException(MaintenanceMessageKeys.CANNOT_GET_INSPECTION + ": " + ex.getMessage());
        }
    }

    @Override
    public InspectionDto uploadHousePhotos(UUID inspectionId, List<MultipartFile> files) {
        InspectionJob job = inspectionJobRepository.findById(inspectionId)
                .orElseThrow(() -> new NotFoundException(MaintenanceMessageKeys.INSPECTION_NOT_FOUND));

        InspectionStatus cur = job.getStatus();
        if (cur != InspectionStatus.IN_PROGRESS && cur != InspectionStatus.DONE) {
            throw new BadRequestException(MaintenanceMessageKeys.INVALID_STATUS_TRANSITION, cur, InspectionStatus.IN_PROGRESS);
        }

        if (files == null || files.isEmpty()) {
            throw new BadRequestException(MaintenanceMessageKeys.INSPECTION_PHOTOS_EMPTY);
        }

        List<String> existing = job.getHousePhotoKeys();
        if (existing == null) existing = new ArrayList<>();
        String folder = "inspections/" + job.getId();
        List<String> newKeys = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) continue;
            newKeys.add(s3Service.upload(file, folder));
        }
        if (newKeys.isEmpty()) {
            throw new BadRequestException(MaintenanceMessageKeys.INSPECTION_PHOTOS_EMPTY);
        }

        existing.addAll(newKeys);
        job.setHousePhotoKeys(existing);
        job.setUpdatedAt(Instant.now());
        InspectionJob saved = inspectionJobRepository.save(job);
        cachedPageService.evictAll(PAGE_NS);
        return mapper.toDto(saved);
    }

    private List<String> resolveHousePhotoUrls(InspectionJob job) {
        if (job == null || job.getHousePhotoKeys() == null || job.getHousePhotoKeys().isEmpty()) {
            return Collections.emptyList();
        }
        return job.getHousePhotoKeys().parallelStream()
                .map(s3Service::getImageUrl)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public InspectionDto updateStatus(UUID id, InspectionStatus newStatus) {
        InspectionJob job = inspectionJobRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(MaintenanceMessageKeys.INSPECTION_NOT_FOUND));

        InspectionStatus cur = job.getStatus();

        if (cur == InspectionStatus.SCHEDULED && newStatus == InspectionStatus.IN_PROGRESS) {
            job.setStatus(newStatus);
        } else if (cur == InspectionStatus.IN_PROGRESS && newStatus == InspectionStatus.DONE) {
            job.setStatus(newStatus);
        } else if (cur == InspectionStatus.DONE && newStatus == InspectionStatus.APPROVED) {
            job.setStatus(newStatus);
        } else {
            throw new BadRequestException(MaintenanceMessageKeys.INVALID_STATUS_TRANSITION, cur, newStatus);
        }

        job.setUpdatedAt(Instant.now());

        InspectionJob saved = inspectionJobRepository.save(job);

        if (newStatus == InspectionStatus.APPROVED) {
            JobEvent event = JobEvent.builder()
                    .referenceId(job.getId())
                    .slotId(job.getSlotId())
                    .staffId(job.getAssignedStaffId())
                    .houseId(job.getHouseId())
                    .referenceType("INSPECTION")
                    .contractId(job.getContractId())
                    .inspectionType(job.getType() != null ? job.getType().name() : null)
                    .action(JobAction.JOB_COMPLETED)
                    .build();

            jobEventProducer.publishJobCompleted(event);
        }
        cachedPageService.evictAll(PAGE_NS);
        return mapper.toDto(saved);
    }

    @Override
    public void markScheduled(JobEvent event) {
        InspectionJob job = inspectionJobRepository.findById(event.getReferenceId())
                .orElseThrow(() -> new RuntimeException("Job not found: " + event.getReferenceId()));

        if (job.getStatus() == InspectionStatus.SCHEDULED) {
            return;
        }

        job.setStatus(InspectionStatus.SCHEDULED);
        job.setAssignedStaffId(event.getStaffId());
        job.setSlotId(event.getSlotId());

        inspectionJobRepository.save(job);

        saveHistory(job, event);
        cachedPageService.evictAll(PAGE_NS);
    }

    private PageResponse<InspectionDto> loadPage(PageRequest request) {
        InspectionStatus statusFilter = request.<String>filterValue("status")
                .map(s -> {
                    try {
                        return InspectionStatus.valueOf(s.toUpperCase().trim());
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .orElse(null);

        InspectionType typeFilter = request.<String>filterValue("type")
                .map(t -> {
                    try {
                        return InspectionType.valueOf(t.toUpperCase().trim());
                    } catch (Exception e) {
                        return null;
                    }
                })
                .orElse(null);

        String statusesRaw = request.<String>filterValue("statuses").orElse(null);

        String houseIdRaw = request.<String>filterValue("houseId").orElse(null);
        UUID houseIdFilter = houseIdRaw != null ? UUID.fromString(houseIdRaw) : null;

        var spec = SpecificationBuilder.<InspectionJob>create()
                .keywordLike(request.keyword(), "note")
                .enumEq("status", statusFilter)
                .enumInRaw("status", statusesRaw, InspectionStatus.class)
                .eq("houseId", houseIdFilter)
                .enumEq("type", typeFilter)
                .build();
        var pageable = SpringPageConverter.toPageable(request);
        Page<InspectionJob> page = inspectionJobRepository.findAll(spec, pageable);
        Map<UUID, HouseResponse> houseCache = loadHouses(page.getContent());
        return SpringPageConverter.fromPage(page, job -> enrichHouse(mapper.toDto(job), job, houseCache));
    }

    private Map<UUID, HouseResponse> loadHouses(Collection<InspectionJob> jobs) {
        Map<UUID, HouseResponse> map = new ConcurrentHashMap<>();
        jobs.stream()
                .map(InspectionJob::getHouseId)
                .filter(Objects::nonNull)
                .distinct()
                .parallel()
                .forEach(id -> {
                    HouseResponse house = fetchHouse(id);
                    if (house != null) {
                        map.put(id, house);
                    }
                });
        return map;
    }

    private HouseResponse fetchHouse(UUID houseId) {
        try {
            return houseClientsGrpc.getHouseById(houseId.toString());
        } catch (Exception ex) {
            log.warn("Unable to enrich inspection with house houseId={}: {}", houseId, ex.getMessage());
            return null;
        }
    }

    private String composeAddress(HouseResponse house) {
        return java.util.stream.Stream.of(house.getAddress(), house.getWard(), house.getCommune(), house.getCity())
                .filter(s -> s != null && !s.isBlank())
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private InspectionDto enrichHouse(InspectionDto dto, InspectionJob job, Map<UUID, HouseResponse> houseCache) {
        HouseResponse house = job.getHouseId() == null ? null : houseCache.get(job.getHouseId());
        String houseName = house != null ? house.getName() : null;
        String houseAddress = house != null ? composeAddress(house) : null;
        return new InspectionDto(
                dto.id(),
                dto.houseId(),
                houseName,
                houseAddress,
                dto.contractId(),
                dto.assignedStaffId(),
                dto.staffName(),
                dto.staffPhone(),
                dto.slotId(),
                dto.status(),
                dto.type(),
                dto.note(),
                dto.housePhotoUrls(),
                dto.createdAt(),
                dto.updatedAt(),
                dto.quote());
    }

    @Override
    public void markSlot(JobEvent event) {
        InspectionJob job = inspectionJobRepository.findById((event.getReferenceId()))
                .orElseThrow(() -> new RuntimeException("Job not found"));

        if (job.getSlotId() != null) {
            return;
        }
        job.setSlotId(event.getSlotId());

        inspectionJobRepository.save(job);
        saveHistory(job,event);
        cachedPageService.evictAll(PAGE_NS);
    }

    @Override
    public void markPendingManagerReview(UUID contractId) {
        if (contractId == null) {
            log.warn("[Inspection] markPendingManagerReview called with null contractId");
            return;
        }
        java.util.List<InspectionStatus> openStatuses = java.util.List.of(
                InspectionStatus.CREATED,
                InspectionStatus.SCHEDULED,
                InspectionStatus.IN_PROGRESS,
                InspectionStatus.PENDING_MANAGER_REVIEW,
                InspectionStatus.DONE
        );
        InspectionJob job = inspectionJobRepository
                .findFirstByContractIdAndStatusInOrderByUpdatedAtDesc(contractId, openStatuses)
                .orElse(null);
        if (job == null) {
            log.warn("[Inspection] No inspection job awaiting manager review for contractId={}",
                    contractId);
            return;
        }
        InspectionStatus prev = job.getStatus();
        if (prev == InspectionStatus.PENDING_MANAGER_REVIEW) {
            return;
        }
        job.setStatus(InspectionStatus.PENDING_MANAGER_REVIEW);
        job.setUpdatedAt(java.time.Instant.now());
        inspectionJobRepository.save(job);
        cachedPageService.evictAll(PAGE_NS);
        log.info("[Inspection] pendingManagerReview jobId={} contractId={} prev={}",
                job.getId(), contractId, prev);
    }

    @Override
    public void markManagerReviewed(UUID contractId, boolean approved) {
        if (contractId == null) {
            log.warn("[Inspection] markManagerReviewed called with null contractId");
            return;
        }
        java.util.List<InspectionStatus> reviewStatuses = java.util.List.of(
                InspectionStatus.CREATED,
                InspectionStatus.SCHEDULED,
                InspectionStatus.IN_PROGRESS,
                InspectionStatus.PENDING_MANAGER_REVIEW,
                InspectionStatus.DONE,
                InspectionStatus.APPROVED
        );
        InspectionJob job = inspectionJobRepository
                .findFirstByContractIdAndStatusInOrderByUpdatedAtDesc(contractId, reviewStatuses)
                .orElse(null);
        if (job == null) {
            log.warn("[Inspection] No reviewed inspection job for contractId={}", contractId);
            return;
        }
        InspectionStatus target = approved ? InspectionStatus.APPROVED : InspectionStatus.DONE;
        InspectionStatus prev = job.getStatus();
        if (prev == target) {
            return;
        }
        job.setStatus(target);
        job.setUpdatedAt(java.time.Instant.now());
        inspectionJobRepository.save(job);
        cachedPageService.evictAll(PAGE_NS);
        log.info("[Inspection] managerReviewed jobId={} contractId={} approved={} prev={} next={}",
                job.getId(), contractId, approved, prev, target);
    }

    private void saveHistory(InspectionJob job, JobEvent event) {
        MaintenanceJobHistory history = new MaintenanceJobHistory();

        history.setJobId(job.getId());
        history.setAction(event.getAction().name());
        history.setActorId(event.getStaffId());
        history.setCreatedAt(Instant.now());

        historyRepository.save(history);
    }

    private String resolveUserLanguageFromKeycloak(String keycloakId) {
        try {
            String lang = userClientsGrpc.getUserIdAndRoleByKeyCloakId(keycloakId).getLanguage();
            if (lang == null || lang.isBlank()) return DEFAULT_LANGUAGE;
            return lang.trim().toLowerCase(Locale.ROOT);
        } catch (Exception ex) {
            return DEFAULT_LANGUAGE;
        }
    }

    private static String resolveNote(InspectionJob job) {
        if (job.getNoteTranslations() != null) {
            String resolved = job.getNoteTranslations().resolve();
            if (resolved != null && !resolved.isBlank()) return resolved;
        }
        return job.getNote();
    }
}

