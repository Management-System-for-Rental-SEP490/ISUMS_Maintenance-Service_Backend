package com.isums.maintainservice.infrastructures.listeners;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.isums.maintainservice.domains.entities.InspectionJob;
import com.isums.maintainservice.domains.enums.JobAction;
import com.isums.maintainservice.domains.events.JobEvent;
import com.isums.maintainservice.infrastructures.abstracts.InspectionJobService;
import com.isums.maintainservice.infrastructures.abstracts.MaintenanceJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JobScheduledEventListeners {

    private final MaintenanceJobService maintenanceJobService;
    private final InspectionJobService inspectionJobService;
    private final ObjectMapper objectMapper;

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

            if (!"MAINTENANCE".equals(event.getReferenceType())
                    || event.getAction() != JobAction.JOB_ASSIGNED) {
                ack.acknowledge();
                return;
            }

            maintenanceJobService.markSlot(event);

            ack.acknowledge();

            log.info("[Maintenance] JOB_ASSIGNED handled jobId={} slotId={}",
                    event.getReferenceId(),
                    event.getSlotId());

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("[Maintenance] Deserialize failed raw={}: {}", record.value(), e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Maintenance] handleAssigned failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}