package com.isums.maintainservice.domains.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobScheduledEvent {
    private UUID jobId;
    private String jobType;
    private UUID slotId;
    private UUID staffId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

}
