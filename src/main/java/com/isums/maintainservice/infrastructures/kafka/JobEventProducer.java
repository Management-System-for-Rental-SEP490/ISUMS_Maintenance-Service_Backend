package com.isums.maintainservice.infrastructures.kafka;

import com.isums.maintainservice.domains.events.JobEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JobEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishJobCreated(JobEvent event) {
        kafkaTemplate.send("job.created", event.getReferenceId().toString(), event);
    }
}
