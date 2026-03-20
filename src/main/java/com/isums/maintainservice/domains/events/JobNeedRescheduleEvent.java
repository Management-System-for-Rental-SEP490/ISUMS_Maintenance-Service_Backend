package com.isums.maintainservice.domains.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;


@NoArgsConstructor
@AllArgsConstructor
public class JobNeedRescheduleEvent extends JobEvent {
    private UUID jobId;
    private String jobType;
    private UUID slotId;
}
