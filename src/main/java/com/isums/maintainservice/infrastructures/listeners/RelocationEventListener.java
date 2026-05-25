package com.isums.maintainservice.infrastructures.listeners;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.isums.maintainservice.domains.events.RelocationReportedEvent;
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

    @KafkaListener(topics = "relocation.reported", groupId = "maintenance-group-v2",
            properties = {"auto.offset.reset:earliest"})
    public void handleRelocationReported(String payload) {
        log.info("[Maintenance] relocation.reported ENTRY len={}",
                payload != null ? payload.length() : -1);
        if (payload == null) {
            log.error("[Maintenance] relocation.reported null payload, skipping");
            return;
        }
        RelocationReportedEvent event;
        try {
            event = objectMapper.readValue(payload, RelocationReportedEvent.class);
        } catch (JacksonException e) {
            log.error("[Maintenance] relocation.reported deserialize failed raw={}: {}",
                    payload, e.getMessage());
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
            log.warn("[Maintenance] relocation.reported processing failed contractId={} - will retry: {}",
                    event.getOldContractId(), e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
