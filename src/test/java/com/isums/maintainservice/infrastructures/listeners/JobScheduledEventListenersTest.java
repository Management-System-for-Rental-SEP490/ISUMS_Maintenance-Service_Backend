package com.isums.maintainservice.infrastructures.listeners;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.isums.maintainservice.domains.dtos.InspectionDto;
import com.isums.maintainservice.domains.enums.InspectionStatus;
import com.isums.maintainservice.domains.enums.InspectionType;
import com.isums.maintainservice.domains.enums.JobAction;
import com.isums.maintainservice.domains.events.JobCreatedEvent;
import com.isums.maintainservice.domains.events.JobEvent;
import com.isums.maintainservice.infrastructures.abstracts.InspectionJobService;
import com.isums.maintainservice.infrastructures.abstracts.MaintenanceJobService;
import common.kafkas.IdempotencyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobScheduledEventListeners")
class JobScheduledEventListenersTest {

    @Mock private MaintenanceJobService maintenanceJobService;
    @Mock private InspectionJobService inspectionJobService;
    @Mock private ObjectMapper objectMapper;
    @Mock private KafkaTemplate<String, Object> kafka;
    @Mock private IdempotencyService idempotencyService;

    @InjectMocks private JobScheduledEventListeners listener;

    @Test
    @DisplayName("handleScheduled routes MAINTENANCE to maintenanceJobService")
    void maintenanceScheduled() throws Exception {
        JobEvent event = JobEvent.builder()
                .referenceId(UUID.randomUUID())
                .referenceType("MAINTENANCE")
                .action(JobAction.JOB_SCHEDULED)
                .build();
        when(objectMapper.readValue(anyString(), eq(JobEvent.class))).thenReturn(event);

        listener.handleScheduled("{}");

        verify(maintenanceJobService).markScheduled(event);
        verify(inspectionJobService, never()).markScheduled(any());
    }

    @Test
    @DisplayName("handleScheduled routes INSPECTION to inspectionJobService")
    void inspectionScheduled() throws Exception {
        JobEvent event = JobEvent.builder()
                .referenceId(UUID.randomUUID())
                .referenceType("INSPECTION")
                .action(JobAction.JOB_SCHEDULED)
                .build();
        when(objectMapper.readValue(anyString(), eq(JobEvent.class))).thenReturn(event);

        listener.handleScheduled("{}");

        verify(inspectionJobService).markScheduled(event);
        verify(maintenanceJobService, never()).markScheduled(any());
    }

    @Test
    @DisplayName("handleScheduled ignores null and malformed payloads")
    void invalidScheduledPayloads() throws Exception {
        listener.handleScheduled(null);
        when(objectMapper.readValue(anyString(), eq(JobEvent.class)))
                .thenThrow(new JsonParseException(null, "bad"));

        listener.handleScheduled("bad");

        verifyNoInteractions(maintenanceJobService, inspectionJobService);
    }

    @Test
    @DisplayName("handleScheduled rethrows downstream failure for retry")
    void scheduledRetries() throws Exception {
        JobEvent event = JobEvent.builder()
                .referenceType("INSPECTION")
                .action(JobAction.JOB_SCHEDULED)
                .build();
        when(objectMapper.readValue(anyString(), eq(JobEvent.class))).thenReturn(event);
        doThrow(new RuntimeException("db down")).when(inspectionJobService).markScheduled(event);

        assertThatThrownBy(() -> listener.handleScheduled("{}"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("handleAssigned routes INSPECTION to inspectionJobService")
    void assignedInspection() throws Exception {
        JobEvent event = JobEvent.builder()
                .referenceId(UUID.randomUUID())
                .slotId(UUID.randomUUID())
                .referenceType("INSPECTION")
                .action(JobAction.JOB_ASSIGNED)
                .build();
        when(objectMapper.readValue(anyString(), eq(JobEvent.class))).thenReturn(event);

        listener.handleAssigned("{}");

        verify(inspectionJobService).markSlot(event);
        verify(maintenanceJobService, never()).markSlot(any());
    }

    @Test
    @DisplayName("handleJobCreated creates inspection and publishes result")
    void jobCreatedHappyPath() throws Exception {
        UUID contractId = UUID.randomUUID();
        UUID houseId = UUID.randomUUID();
        UUID inspectionId = UUID.randomUUID();
        String messageId = UUID.randomUUID().toString();
        JobCreatedEvent event = JobCreatedEvent.builder()
                .referenceId(contractId)
                .houseId(houseId)
                .referenceType("INSPECTION")
                .type("CHECK_IN")
                .messageId(messageId)
                .build();
        InspectionDto created = new InspectionDto(
                inspectionId, houseId, contractId, null, null, null, null,
                InspectionStatus.CREATED, InspectionType.CHECK_IN, "x",
                null, null, null, null);
        when(objectMapper.readValue(anyString(), eq(JobCreatedEvent.class))).thenReturn(event);
        when(idempotencyService.isDuplicate(messageId)).thenReturn(false);
        when(inspectionJobService.createFromEvent(event)).thenReturn(created);

        listener.handleJobCreated("{}");

        verify(inspectionJobService).createFromEvent(event);
        verify(idempotencyService).markProcessed(messageId);
        verify(kafka).send(
                eq("job.inspection.created"),
                eq(inspectionId.toString()),
                any(JobCreatedEvent.class));
    }

    @Test
    @DisplayName("handleJobCreated skips duplicate event")
    void jobCreatedDuplicate() throws Exception {
        String messageId = UUID.randomUUID().toString();
        JobCreatedEvent event = JobCreatedEvent.builder()
                .referenceId(UUID.randomUUID())
                .houseId(UUID.randomUUID())
                .referenceType("INSPECTION")
                .type("CHECK_IN")
                .messageId(messageId)
                .build();
        when(objectMapper.readValue(anyString(), eq(JobCreatedEvent.class))).thenReturn(event);
        when(idempotencyService.isDuplicate(messageId)).thenReturn(true);

        listener.handleJobCreated("{}");

        verify(inspectionJobService, never()).createFromEvent(any());
    }
}
