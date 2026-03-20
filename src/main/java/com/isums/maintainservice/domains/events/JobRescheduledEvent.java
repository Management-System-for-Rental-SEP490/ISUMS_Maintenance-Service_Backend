package com.isums.maintainservice.domains.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;


@NoArgsConstructor
@AllArgsConstructor

public class JobRescheduledEvent  extends JobEvent{
    private UUID jobId;
    private String jobType;
    private UUID slotId;
    private UUID staffId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
