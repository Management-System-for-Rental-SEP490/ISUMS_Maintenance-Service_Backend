package com.isums.maintainservice.infrastructures.kafka;

import com.isums.maintainservice.domains.events.JobEvent;
import com.isums.maintainservice.domains.events.SlotEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SlotProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendSlotEvent(SlotEvent event) {
        kafkaTemplate.send("slot.status.topic", event);
    }
}
