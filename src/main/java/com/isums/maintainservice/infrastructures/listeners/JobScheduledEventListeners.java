package com.isums.maintainservice.infrastructures.listeners;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.isums.maintainservice.domains.dtos.InspectionDto;
import com.isums.maintainservice.domains.enums.JobAction;
import com.isums.maintainservice.domains.events.JobCreatedEvent;
import com.isums.maintainservice.domains.events.JobEvent;
import com.isums.maintainservice.infrastructures.abstracts.InspectionJobService;
import com.isums.maintainservice.infrastructures.abstracts.MaintenanceJobService;
import common.kafkas.IdempotencyService;
import common.kafkas.KafkaListenerHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JobScheduledEventListeners {

    private static final String JOB_CREATED_DLQ_TOPIC = "job.created-dlq";

    private final MaintenanceJobService maintenanceJobService;
    private final InspectionJobService inspectionJobService;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafka;
    private final IdempotencyService idempotencyService;
    private final KafkaListenerHelper kafkaHelper;

    @KafkaListener(topics = "job.scheduled", groupId = "maintenance-group")
    public void handleScheduled(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JobEvent event = objectMapper.readValue(record.value(), JobEvent.class);

            if (!List.of("MAINTENANCE", "INSPECTION").contains(event.getReferenceType())
                    || event.getAction() != JobAction.JOB_SCHEDULED) {
                ack.acknowledge();
                return;
            }

            switch (event.getReferenceType()) {

                case "MAINTENANCE" -> {
                    maintenanceJobService.markScheduled(event);
                }

                case "INSPECTION" -> {
                    inspectionJobService.markScheduled(event);
                }

                default -> {
                    log.warn("[Maintenance] Unsupported jobType={}, skip",
                            event.getReferenceType());
                }
            }
                ack.acknowledge();

            log.info("[Maintenance] JOB_SCHEDULED handled jobId={} slotId={} type={}",
                    event.getReferenceId(),
                    event.getSlotId(),
                    event.getReferenceType());

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("[Maintenance] Deserialize failed raw={}: {}", record.value(), e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Maintenance] handleScheduled failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "job.rescheduled", groupId = "maintenance-group")
    public void handleRescheduled(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JobEvent event = objectMapper.readValue(record.value(), JobEvent.class);

            if (!List.of("MAINTENANCE", "INSPECTION").contains(event.getReferenceType())
                    || event.getAction() != JobAction.JOB_RESCHEDULED) {
                ack.acknowledge();
                return;
            }

            maintenanceJobService.markRescheduled(event);

            ack.acknowledge();

            log.info("[Maintenance] JOB_RESCHEDULED handled jobId={}",
                    event.getReferenceId());

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("[Maintenance] Deserialize failed raw={}: {}", record.value(), e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Maintenance] handleRescheduled failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "job.need-reschedule", groupId = "maintenance-group")
    public void handleNeedReschedule(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JobEvent event = objectMapper.readValue(record.value(), JobEvent.class);

            if (!List.of("MAINTENANCE", "INSPECTION").contains(event.getReferenceType())
                    || event.getAction() != JobAction.JOB_NEED_RESCHEDULE) {
                ack.acknowledge();
                return;
            }

            maintenanceJobService.markNeedReschedule(event);

            ack.acknowledge();

            log.info("[Maintenance] JOB_NEED_RESCHEDULE handled jobId={}",
                    event.getReferenceId());

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("[Maintenance] Deserialize failed raw={}: {}", record.value(), e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Maintenance] handleNeedReschedule failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "job.assigned", groupId = "maintenance-group")
    public void handleAssigned(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            JobEvent event = objectMapper.readValue(record.value(), JobEvent.class);

            if (!List.of("MAINTENANCE", "INSPECTION").contains(event.getReferenceType())
                    || event.getAction() != JobAction.JOB_ASSIGNED) {
                ack.acknowledge();
                return;
            }

            switch (event.getReferenceType()) {

                case "MAINTENANCE" -> {
                    maintenanceJobService.markSlot(event);
                }

                case "INSPECTION" -> {
                    inspectionJobService.markSlot(event);
                }

                default -> {
                    log.warn("[Maintenance] Unsupported jobType={}, skip",
                            event.getReferenceType());
                }
            }


            ack.acknowledge();

            log.info("[Maintenance] JOB_ASSIGNED handled jobId={} slotId={} type={}",
                    event.getReferenceId(),
                    event.getSlotId(),
                    event.getReferenceType());

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("[Maintenance] Deserialize failed raw={}: {}", record.value(), e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Maintenance] handleAssigned failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "job.created", groupId = "maintenance-group")
    public void handleJobCreated(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String messageId = kafkaHelper.extractMessageId(record);
        kafkaHelper.setupMDC(record, messageId);
        try {
            if (idempotencyService.isDuplicate(messageId)) {
                log.info("[Maintenance] job.created duplicate skipped messageId={}", messageId);
                ack.acknowledge();
                return;
            }

            JobCreatedEvent event = parseJobCreatedEvent(record);
            if (event == null) {
                publishToDlq(record, "PARSE_FAILED");
                ack.acknowledge();
                return;
            }

            if (!"INSPECTION".equals(event.getReferenceType())) {
                log.debug("[Maintenance] job.created not INSPECTION (={}), skip messageId={}",
                        event.getReferenceType(), messageId);
                idempotencyService.markProcessed(messageId);
                ack.acknowledge();
                return;
            }

            if (event.getType() == null || event.getType().isBlank()
                    || event.getHouseId() == null || event.getReferenceId() == null) {
                log.error("[Maintenance] job.created missing required fields type={} houseId={} refId={} messageId={}",
                        event.getType(), event.getHouseId(), event.getReferenceId(), messageId);
                publishToDlq(record, "MISSING_REQUIRED_FIELDS");
                ack.acknowledge();
                return;
            }

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

            idempotencyService.markProcessed(messageId);
            ack.acknowledge();
            log.info("[Maintenance] InspectionJob created inspectionId={} type={} contractId={} messageId={}",
                    job.id(), event.getType(), event.getReferenceId(), messageId);

        } catch (Exception e) {
            log.error("[Maintenance] handleJobCreated failed messageId={} — will retry: {}",
                    messageId, e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            kafkaHelper.clearMDC();
        }
    }

    private JobCreatedEvent parseJobCreatedEvent(ConsumerRecord<String, String> record) {
        try {
            JobCreatedEvent event = objectMapper.readValue(record.value(), JobCreatedEvent.class);
            if (event != null && event.getType() != null) return event;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("[Maintenance] Strict JSON parse failed, fallback to Map: {}", e.getMessage());
        }
        try {
            java.util.Map<String, Object> raw = objectMapper.readValue(
                    record.value(),
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
            return JobCreatedEvent.builder()
                    .referenceId(parseUuid(raw.get("referenceId")))
                    .houseId(parseUuid(raw.get("houseId")))
                    .referenceType(asString(raw.get("referenceType")))
                    .type(asString(raw.get("type")))
                    .messageId(asString(raw.get("messageId")))
                    .build();
        } catch (Exception e) {
            log.error("[Maintenance] Map fallback parse failed raw={}: {}", record.value(), e.getMessage());
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

    private void publishToDlq(ConsumerRecord<String, String> record, String reason) {
        try {
            kafka.send(JOB_CREATED_DLQ_TOPIC, record.key(), record.value())
                    .whenComplete((r, ex) -> {
                        if (ex != null) {
                            log.error("[Maintenance] DLQ publish failed reason={}: {}", reason, ex.toString());
                        } else {
                            log.warn("[Maintenance] Routed to DLQ reason={} originalOffset={} key={}",
                                    reason, record.offset(), record.key());
                        }
                    });
        } catch (Exception e) {
            log.error("[Maintenance] DLQ publish threw reason={}: {}", reason, e.getMessage(), e);
        }
    }
}