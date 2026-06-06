package com.isums.maintainservice.infrastructures.listeners;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.isums.maintainservice.domains.events.RelocationReportedEvent;
import com.isums.maintainservice.domains.events.RelocationReviewedEvent;
import com.isums.maintainservice.infrastructures.abstracts.InspectionJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RelocationEventListener {

    private final InspectionJobService inspectionJobService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "relocation.reported", groupId = "maintenance-group")
    public void handleRelocationReported(String payload) {
        RelocationReportedEvent event = read(payload, RelocationReportedEvent.class, "relocation.reported");
        if (event == null) {
            return;
        }
        if (event.getOldContractId() == null) {
            log.warn("[Maintenance] relocation.reported missing oldContractId, skip");
            return;
        }
        try {
            inspectionJobService.markPendingManagerReview(event.getOldContractId());
            log.info("[Maintenance] relocation.reported handled relocationId={} contractId={}",
                    event.getRelocationRequestId(), event.getOldContractId());
        } catch (Exception e) {
            log.error("[Maintenance] relocation.reported processing failed contractId={}: {}",
                    event.getOldContractId(), e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "relocation.reviewed", groupId = "maintenance-relocation-review")
    public void handleRelocationReviewed(String payload) {
        RelocationReviewedEvent event = read(payload, RelocationReviewedEvent.class, "relocation.reviewed");
        if (event == null) {
            return;
        }
        if (event.getOldContractId() == null) {
            log.warn("[Maintenance] relocation.reviewed missing oldContractId, skip");
            return;
        }
        try {
            inspectionJobService.markManagerReviewed(event.getOldContractId(), event.isApproved());
            log.info("[Maintenance] relocation.reviewed handled relocationId={} contractId={} approved={}",
                    event.getRelocationRequestId(), event.getOldContractId(), event.isApproved());
        } catch (Exception e) {
            log.error("[Maintenance] relocation.reviewed processing failed contractId={}: {}",
                    event.getOldContractId(), e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private <T> T read(String payload, Class<T> eventType, String topic) {
        if (payload == null || payload.isBlank()) {
            log.error("[Maintenance] {} empty payload, skipping", topic);
            return null;
        }
        try {
            return objectMapper.readValue(payload, eventType);
        } catch (JacksonException e) {
            log.error("[Maintenance] {} deserialize failed raw={}: {}", topic, payload, e.getMessage());
            return null;
        }
    }
}
