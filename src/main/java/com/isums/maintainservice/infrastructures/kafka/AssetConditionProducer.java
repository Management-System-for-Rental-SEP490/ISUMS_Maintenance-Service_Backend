package com.isums.maintainservice.infrastructures.kafka;

import com.isums.maintainservice.domains.events.AssetConditionEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AssetConditionProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendConditionUpdate(AssetConditionEvent event){
        kafkaTemplate.send("asset-condition-update-topic", event);

    }
}
