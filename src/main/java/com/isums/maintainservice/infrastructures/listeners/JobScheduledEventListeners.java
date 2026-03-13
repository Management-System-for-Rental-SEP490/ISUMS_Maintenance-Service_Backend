package com.isums.maintainservice.infrastructures.listeners;

import com.isums.maintainservice.domains.events.JobScheduledEvent;
import com.isums.maintainservice.infrastructures.abstracts.MaintenanceJobService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobScheduledEventListeners {
    private final MaintenanceJobService maintenanceJobService;

    @KafkaListener(topics = "job.scheduled", groupId = "maintenance-group")
    public void handle(JobScheduledEvent event) {

        if (!event.getJobType().equals("MAINTENANCE") && !event.getJobType().equals("ISSUE")) {
            return;
        }

        maintenanceJobService.markScheduled(event);

    }

}
