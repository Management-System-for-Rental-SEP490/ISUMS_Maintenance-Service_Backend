package com.isums.maintainservice.infrastructures.listeners;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.isums.maintainservice.domains.dtos.InspectionDto;
import com.isums.maintainservice.domains.enums.JobAction;
import com.isums.maintainservice.domains.events.JobCreatedEvent;
import com.isums.maintainservice.domains.events.JobEvent;
import com.isums.maintainservice.infrastructures.abstracts.InspectionJobService;
import com.isums.maintainservice.infrastructures.abstracts.MaintenanceJobService;
import common.kafkas.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JobScheduledEventListeners {

    private static final String JOB_CREATED_DLQ_TOPIC = "job.created-dlq";
    private static final String GROUP = "maintenance-group-v2";

    private final MaintenanceJobService maintenanceJobService;
    private final InspectionJobService inspectionJobService;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafka;
    private final IdempotencyService idempotencyService;

    @KafkaListener(topics = "job.scheduled", groupId = GROUP,
            properties = {"auto.offset.reset:earliest"})
    public void handleScheduled(String payload) {
        log.info("[Maintenance] handleScheduled ENTRY len={}",
                payload != null ? payload.length() : -1);
        if (payload == null) {
            log.error("[Maintenance] handleScheduled null payload, skipping");
            return;
        }
        JobEvent event;
        try {
            event = objectMapper.readValue(payload, JobEvent.class);
        } catch (JacksonException e) {
            log.error("[Maintenance] handleScheduled deserialize failed: {}", e.getMessage());
            return;
        }
        if (!List.of("MAINTENANCE", "INSPECTION").contains(event.getReferenceType())
                || event.getAction() != JobAction.JOB_SCHEDULED) {
            return;
        }
        try {
            switch (event.getReferenceType()) {
                case "MAINTENANCE" -> maintenanceJobService.markScheduled(event);
                case "INSPECTION" -> inspectionJobService.markScheduled(event);
                default -> log.warn("[Maintenance] Unsupported jobType={}, skip", event.getReferenceType());
            }
            log.info("[Maintenance] JOB_SCHEDULED handled jobId={} slotId={} type={}",
                    event.getReferenceId(), event.getSlotId(), event.getReferenceType());
        } catch (Exception e) {
            log.warn("[Maintenance] handleScheduled failed jobId={} - will retry: {}",
                    event.getReferenceId(), e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "job.rescheduled", groupId = GROUP,
            properties = {"auto.offset.reset:earliest"})
    public void handleRescheduled(String payload) {
        log.info("[Maintenance] handleRescheduled ENTRY len={}",
                payload != null ? payload.length() : -1);
        if (payload == null) return;
        JobEvent event;
        try {
            event = objectMapper.readValue(payload, JobEvent.class);
        } catch (JacksonException e) {
            log.error("[Maintenance] handleRescheduled deserialize failed: {}", e.getMessage());
            return;
        }
        if (!List.of("MAINTENANCE", "INSPECTION").contains(event.getReferenceType())
                || event.getAction() != JobAction.JOB_RESCHEDULED) {
            return;
        }
        try {
            maintenanceJobService.markRescheduled(event);
            log.info("[Maintenance] JOB_RESCHEDULED handled jobId={}", event.getReferenceId());
        } catch (Exception e) {
            log.warn("[Maintenance] handleRescheduled failed jobId={} - will retry: {}",
                    event.getReferenceId(), e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "job.need-reschedule", groupId = GROUP,
            properties = {"auto.offset.reset:earliest"})
    public void handleNeedReschedule(String payload) {
        log.info("[Maintenance] handleNeedReschedule ENTRY len={}",
                payload != null ? payload.length() : -1);
        if (payload == null) return;
        JobEvent event;
        try {
            event = objectMapper.readValue(payload, JobEvent.class);
        } catch (JacksonException e) {
            log.error("[Maintenance] handleNeedReschedule deserialize failed: {}", e.getMessage());
            return;
        }
        if (!List.of("MAINTENANCE", "INSPECTION").contains(event.getReferenceType())
                || event.getAction() != JobAction.JOB_NEED_RESCHEDULE) {
            return;
        }
        try {
            maintenanceJobService.markNeedReschedule(event);
            log.info("[Maintenance] JOB_NEED_RESCHEDULE handled jobId={}", event.getReferenceId());
        } catch (Exception e) {
            log.warn("[Maintenance] handleNeedReschedule failed jobId={} - will retry: {}",
                    event.getReferenceId(), e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "job.assigned", groupId = GROUP,
            properties = {"auto.offset.reset:earliest"})
    public void handleAssigned(String payload) {
        log.info("[Maintenance] handleAssigned ENTRY len={}",
                payload != null ? payload.length() : -1);
        if (payload == null) return;
        JobEvent event;
        try {
            event = objectMapper.readValue(payload, JobEvent.class);
        } catch (JacksonException e) {
            log.error("[Maintenance] handleAssigned deserialize failed: {}", e.getMessage());
            return;
        }
        if (!List.of("MAINTENANCE", "INSPECTION").contains(event.getReferenceType())
                || event.getAction() != JobAction.JOB_ASSIGNED) {
            return;
        }
        try {
            switch (event.getReferenceType()) {
                case "MAINTENANCE" -> maintenanceJobService.markSlot(event);
                case "INSPECTION" -> inspectionJobService.markSlot(event);
                default -> log.warn("[Maintenance] Unsupported jobType={}, skip", event.getReferenceType());
            }
            log.info("[Maintenance] JOB_ASSIGNED handled jobId={} slotId={} type={}",
                    event.getReferenceId(), event.getSlotId(), event.getReferenceType());
        } catch (Exception e) {
            log.warn("[Maintenance] handleAssigned failed jobId={} - will retry: {}",
                    event.getReferenceId(), e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "job.created", groupId = GROUP,
            properties = {"auto.offset.reset:earliest"})
    public void handleJobCreated(String payload) {
        log.info("[Maintenance] handleJobCreated ENTRY len={}",
                payload != null ? payload.length() : -1);
        if (payload == null) {
            log.error("[Maintenance] handleJobCreated null payload, skipping");
            return;
        }

        JobCreatedEvent event = parseJobCreatedEvent(payload);
        if (event == null) {
            log.error("[Maintenance] handleJobCreated parse failed - sending to DLQ");
            publishToDlq(payload, "PARSE_FAILED");
            return;
        }

        if (!"INSPECTION".equals(event.getReferenceType())) {
            return;
        }

        if (event.getType() == null || event.getType().isBlank()
                || event.getHouseId() == null || event.getReferenceId() == null) {
            log.error("[Maintenance] job.created missing required fields type={} houseId={} refId={}",
                    event.getType(), event.getHouseId(), event.getReferenceId());
            publishToDlq(payload, "MISSING_REQUIRED_FIELDS");
            return;
        }

        String dedupKey = event.getMessageId() != null
                ? event.getMessageId()
                : "ref:" + event.getReferenceId() + ":" + event.getType();
        if (idempotencyService.isDuplicate(dedupKey)) {
            log.info("[Maintenance] job.created duplicate skipped dedupKey={}", dedupKey);
            return;
        }

        try {
            InspectionDto job = inspectionJobService.createFromEvent(event);

            kafka.send("job.inspection.created",
                    job.id().toString(),
                    JobCreatedEvent.builder()
                            .referenceId(job.id())
                            .houseId(event.getHouseId())
                            .referenceType("INSPECTION")
                            .type(event.getType())
                            .messageId(UUID.randomUUID().toString())
                            .build());

            idempotencyService.markProcessed(dedupKey);
            log.info("[Maintenance] InspectionJob created inspectionId={} type={} contractId={} dedupKey={}",
                    job.id(), event.getType(), event.getReferenceId(), dedupKey);

        } catch (Exception e) {
            log.warn("[Maintenance] handleJobCreated failed dedupKey={} - will retry: {}",
                    dedupKey, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private JobCreatedEvent parseJobCreatedEvent(String payload) {
        try {
            JobCreatedEvent event = objectMapper.readValue(payload, JobCreatedEvent.class);
            if (event != null && event.getType() != null) return event;
        } catch (JacksonException e) {
            log.warn("[Maintenance] Strict JSON parse failed, fallback to Map: {}", e.getMessage());
        }
        try {
            java.util.Map<String, Object> raw = objectMapper.readValue(
                    payload,
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
            return JobCreatedEvent.builder()
                    .referenceId(parseUuid(raw.get("referenceId")))
                    .houseId(parseUuid(raw.get("houseId")))
                    .referenceType(asString(raw.get("referenceType")))
                    .type(asString(raw.get("type")))
                    .messageId(asString(raw.get("messageId")))
                    .build();
        } catch (Exception e) {
            log.error("[Maintenance] Map fallback parse failed raw={}: {}", payload, e.getMessage());
            return null;
        }
    }

    private static UUID parseUuid(Object value) {
        if (value == null) return null;
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private void publishToDlq(String payload, String reason) {
        try {
            kafka.send(JOB_CREATED_DLQ_TOPIC, reason, payload)
                    .whenComplete((r, ex) -> {
                        if (ex != null) {
                            log.error("[Maintenance] DLQ publish failed reason={}: {}", reason, ex.toString());
                        } else {
                            log.warn("[Maintenance] Routed to DLQ reason={}", reason);
                        }
                    });
        } catch (Exception e) {
            log.error("[Maintenance] DLQ publish threw reason={}: {}", reason, e.getMessage(), e);
        }
    }
}
