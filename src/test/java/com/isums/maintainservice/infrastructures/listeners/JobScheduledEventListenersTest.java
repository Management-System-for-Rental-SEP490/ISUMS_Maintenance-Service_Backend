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
        JobEvent evt = JobEvent.builder()
                .referenceId(UUID.randomUUID())
                .referenceType("MAINTENANCE").action(JobAction.JOB_SCHEDULED).build();
        when(objectMapper.readValue(anyString(), eq(JobEvent.class))).thenReturn(evt);

        listener.handleScheduled("{}");

        verify(maintenanceJobService).markScheduled(evt);
        verify(inspectionJobService, never()).markScheduled(any());
    }

    @Test
    @DisplayName("handleScheduled routes INSPECTION to inspectionJobService")
    void inspectionScheduled() throws Exception {
        JobEvent evt = JobEvent.builder()
                .referenceId(UUID.randomUUID())
                .referenceType("INSPECTION").action(JobAction.JOB_SCHEDULED).build();
        when(objectMapper.readValue(anyString(), eq(JobEvent.class))).thenReturn(evt);

        listener.handleScheduled("{}");

        verify(inspectionJobService).markScheduled(evt);
        verify(maintenanceJobService, never()).markScheduled(any());
    }

    @Test
    @DisplayName("handleScheduled swallows null payload (no work)")
    void scheduledNullPayload() {
        listener.handleScheduled(null);
        verifyNoInteractions(maintenanceJobService, inspectionJobService);
    }

    @Test
    @DisplayName("handleScheduled swallows JSON parse error (no retry, poison-pill)")
    void scheduledBadJson() throws Exception {
        when(objectMapper.readValue(anyString(), eq(JobEvent.class)))
                .thenThrow(new JsonParseException(null, "bad"));

        listener.handleScheduled("{}");

        verifyNoInteractions(maintenanceJobService, inspectionJobService);
    }

    @Test
    @DisplayName("handleScheduled rethrows on downstream failure (Kafka retries)")
    void scheduledRetries() throws Exception {
        JobEvent evt = JobEvent.builder()
                .referenceType("INSPECTION").action(JobAction.JOB_SCHEDULED).build();
        when(objectMapper.readValue(anyString(), eq(JobEvent.class))).thenReturn(evt);
        doThrow(new RuntimeException("db down")).when(inspectionJobService).markScheduled(evt);

        assertThatThrownBy(() -> listener.handleScheduled("{}"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("handleScheduled ignores events whose action != JOB_SCHEDULED")
    void scheduledWrongAction() throws Exception {
        JobEvent evt = JobEvent.builder()
                .referenceType("INSPECTION").action(JobAction.JOB_CREATED).build();
        when(objectMapper.readValue(anyString(), eq(JobEvent.class))).thenReturn(evt);

        listener.handleScheduled("{}");

        verify(inspectionJobService, never()).markScheduled(any());
    }

    @Test
    @DisplayName("handleAssigned routes INSPECTION to inspectionJobService.markSlot")
    void assignedInspection() throws Exception {
        JobEvent evt = JobEvent.builder()
                .referenceId(UUID.randomUUID())
                .slotId(UUID.randomUUID())
                .referenceType("INSPECTION").action(JobAction.JOB_ASSIGNED).build();
        when(objectMapper.readValue(anyString(), eq(JobEvent.class))).thenReturn(evt);

        listener.handleAssigned("{}");

        verify(inspectionJobService).markSlot(evt);
        verify(maintenanceJobService, never()).markSlot(any());
    }

    @Test
    @DisplayName("handleJobCreated swallows null payload")
    void jobCreatedNullPayload() {
        listener.handleJobCreated(null);
        verifyNoInteractions(maintenanceJobService, inspectionJobService);
    }

    @Test
    @DisplayName("handleJobCreated skips non-INSPECTION referenceType")
    void jobCreatedNonInspection() throws Exception {
        JobCreatedEvent evt = JobCreatedEvent.builder()
                .referenceId(UUID.randomUUID())
                .houseId(UUID.randomUUID())
                .referenceType("MAINTENANCE")
                .type("CHECK_IN")
                .messageId(UUID.randomUUID().toString())
                .build();
        when(objectMapper.readValue(anyString(), eq(JobCreatedEvent.class))).thenReturn(evt);

        listener.handleJobCreated("{}");

        verifyNoInteractions(inspectionJobService);
    }

    @Test
    @DisplayName("handleJobCreated creates inspection then publishes job.inspection.created")
    void jobCreatedHappyPath() throws Exception {
        UUID contractId = UUID.randomUUID();
        UUID houseId = UUID.randomUUID();
        UUID inspectionId = UUID.randomUUID();
        String messageId = UUID.randomUUID().toString();

        JobCreatedEvent evt = JobCreatedEvent.builder()
                .referenceId(contractId)
                .houseId(houseId)
                .referenceType("INSPECTION")
                .type("CHECK_IN")
                .messageId(messageId)
                .build();
        when(objectMapper.readValue(anyString(), eq(JobCreatedEvent.class))).thenReturn(evt);
        when(idempotencyService.isDuplicate(messageId)).thenReturn(false);

        InspectionDto created = new InspectionDto(
                inspectionId, houseId, contractId, null, null, null, null,
                InspectionStatus.CREATED, InspectionType.CHECK_IN, "x", null, null, null, null);
        when(inspectionJobService.createFromEvent(evt)).thenReturn(created);

        listener.handleJobCreated("{}");

        verify(inspectionJobService).createFromEvent(evt);
        verify(idempotencyService).markProcessed(messageId);
        verify(kafka).send(eq("job.inspection.created"), eq(inspectionId.toString()), any());
    }

    @Test
    @DisplayName("handleJobCreated dedupes by messageId — duplicate skipped, no createFromEvent")
    void jobCreatedDeduped() throws Exception {
        String messageId = UUID.randomUUID().toString();
        JobCreatedEvent evt = JobCreatedEvent.builder()
                .referenceId(UUID.randomUUID())
                .houseId(UUID.randomUUID())
                .referenceType("INSPECTION")
                .type("CHECK_IN")
                .messageId(messageId)
                .build();
        when(objectMapper.readValue(anyString(), eq(JobCreatedEvent.class))).thenReturn(evt);
        when(idempotencyService.isDuplicate(messageId)).thenReturn(true);

        listener.handleJobCreated("{}");

        verify(inspectionJobService, never()).createFromEvent(any());
    }

    @Test
    @DisplayName("handleJobCreated missing required field (houseId) — routes to DLQ instead of crashing")
    void jobCreatedMissingHouseId() throws Exception {
        JobCreatedEvent evt = JobCreatedEvent.builder()
                .referenceId(UUID.randomUUID())
                .houseId(null)
                .referenceType("INSPECTION")
                .type("CHECK_IN")
                .messageId(UUID.randomUUID().toString())
                .build();
        when(objectMapper.readValue(anyString(), eq(JobCreatedEvent.class))).thenReturn(evt);

        listener.handleJobCreated("{}");

        verify(inspectionJobService, never()).createFromEvent(any());
        verify(kafka).send(eq("job.created-dlq"), eq("MISSING_REQUIRED_FIELDS"), anyString());
    }
}
