package com.isums.maintainservice.infrastructures.listeners;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.isums.maintainservice.domains.events.RelocationReportedEvent;
import com.isums.maintainservice.infrastructures.abstracts.InspectionJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RelocationEventListener {

    private final InspectionJobService inspectionJobService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "relocation.reported", groupId = "maintenance-group")
    public void handleRelocationReported(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            RelocationReportedEvent event = objectMapper.readValue(record.value(), RelocationReportedEvent.class);
            if (event.getOldContractId() == null) {
                log.warn("[Maintenance] relocation.reported missing oldContractId, skip");
                ack.acknowledge();
                return;
            }
            inspectionJobService.markPendingManagerReview(event.getOldContractId());
            ack.acknowledge();
            log.info("[Maintenance] relocation.reported handled relocationId={} contractId={}",
                    event.getRelocationRequestId(), event.getOldContractId());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("[Maintenance] relocation.reported deserialize failed raw={}: {}",
                    record.value(), e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Maintenance] relocation.reported processing failed, will retry: {}",
                    e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
