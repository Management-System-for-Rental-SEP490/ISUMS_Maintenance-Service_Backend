package com.isums.maintainservice.infrastructures.listeners;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.isums.maintainservice.domains.dtos.InspectionDto;
import com.isums.maintainservice.domains.enums.InspectionStatus;
import com.isums.maintainservice.domains.enums.InspectionType;
import com.isums.maintainservice.domains.enums.JobAction;
import com.isums.maintainservice.domains.events.JobCreatedEvent;
import com.isums.maintainservice.domains.events.JobEvent;
import com.isums.maintainservice.infrastructures.abstracts.InspectionJobService;
import com.isums.maintainservice.infrastructures.abstracts.MaintenanceJobService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobScheduledEventListeners")
class JobScheduledEventListenersTest {

    @Mock private MaintenanceJobService maintenanceJobService;
    @Mock private InspectionJobService inspectionJobService;
    @Mock private ObjectMapper objectMapper;
    @Mock private KafkaTemplate<String, Object> kafka;
    @Mock private Acknowledgment ack;

    @InjectMocks private JobScheduledEventListeners listener;

    private ConsumerRecord<String, String> record(String payload) {
        return new ConsumerRecord<>("t", 0, 0L, "k", payload);
    }

    @Test
    @DisplayName("handleScheduled routes MAINTENANCE to maintenanceJobService and acks")
    void maintenanceScheduled() throws Exception {
        JobEvent evt = JobEvent.builder()
                .referenceId(UUID.randomUUID())
                .referenceType("MAINTENANCE").action(JobAction.JOB_SCHEDULED).build();

        when(objectMapper.readValue(anyString(), eq(JobEvent.class))).thenReturn(evt);

        listener.handleScheduled(record("{}"), ack);

        verify(maintenanceJobService).markScheduled(evt);
        verify(inspectionJobService, never()).markScheduled(any());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("handleScheduled routes INSPECTION to inspectionJobService")
    void inspectionScheduled() throws Exception {
        JobEvent evt = JobEvent.builder()
                .referenceId(UUID.randomUUID())
                .referenceType("INSPECTION").action(JobAction.JOB_SCHEDULED).build();
        when(objectMapper.readValue(anyString(), eq(JobEvent.class))).thenReturn(evt);

        listener.handleScheduled(record("{}"), ack);

        verify(inspectionJobService).markScheduled(evt);
        verify(maintenanceJobService, never()).markScheduled(any());
    }

    @Test
    @DisplayName("handleScheduled skips unsupported reference type and still acks")
    void unsupported() throws Exception {
        JobEvent evt = JobEvent.builder()
                .referenceType("ISSUE").action(JobAction.JOB_SCHEDULED).build();
        when(objectMapper.readValue(anyString(), eq(JobEvent.class))).thenReturn(evt);

        listener.handleScheduled(record("{}"), ack);

        verify(maintenanceJobService, never()).markScheduled(any());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("handleScheduled acks poison-pill payload")
    void poison() throws Exception {
        when(objectMapper.readValue(anyString(), eq(JobEvent.class)))
                .thenThrow(new JsonProcessingException("bad") {});

        listener.handleScheduled(record("bad"), ack);

        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("handleScheduled rethrows unexpected errors so Kafka retries")
    void rethrowsUnexpected() throws Exception {
        JobEvent evt = JobEvent.builder()
                .referenceType("MAINTENANCE").action(JobAction.JOB_SCHEDULED).build();
        when(objectMapper.readValue(anyString(), eq(JobEvent.class))).thenReturn(evt);
        org.mockito.Mockito.doThrow(new RuntimeException("boom"))
                .when(maintenanceJobService).markScheduled(evt);

        assertThatThrownBy(() -> listener.handleScheduled(record("{}"), ack))
                .isInstanceOf(RuntimeException.class);
        verify(ack, never()).acknowledge();
    }

    @Test
    @DisplayName("handleRescheduled delegates to maintenance.markRescheduled when type=MAINTENANCE")
    void rescheduled() throws Exception {
        JobEvent evt = JobEvent.builder()
                .referenceType("MAINTENANCE").action(JobAction.JOB_RESCHEDULED).build();
        when(objectMapper.readValue(anyString(), eq(JobEvent.class))).thenReturn(evt);

        listener.handleRescheduled(record("{}"), ack);

        verify(maintenanceJobService).markRescheduled(evt);
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("handleNeedReschedule delegates on matching type/action")
    void needReschedule() throws Exception {
        JobEvent evt = JobEvent.builder()
                .referenceType("MAINTENANCE").action(JobAction.JOB_NEED_RESCHEDULE).build();
        when(objectMapper.readValue(anyString(), eq(JobEvent.class))).thenReturn(evt);

        listener.handleNeedReschedule(record("{}"), ack);

        verify(maintenanceJobService).markNeedReschedule(evt);
    }

    @Test
    @DisplayName("handleAssigned delegates to markSlot when type=MAINTENANCE")
    void assigned() throws Exception {
        JobEvent evt = JobEvent.builder()
                .referenceType("MAINTENANCE").action(JobAction.JOB_ASSIGNED).build();
        when(objectMapper.readValue(anyString(), eq(JobEvent.class))).thenReturn(evt);

        listener.handleAssigned(record("{}"), ack);

        verify(maintenanceJobService).markSlot(evt);
    }

    @Test
    @DisplayName("handleAssigned skips non-MAINTENANCE events")
    void assignedWrongType() throws Exception {
        JobEvent evt = JobEvent.builder()
                .referenceType("INSPECTION").action(JobAction.JOB_ASSIGNED).build();
        when(objectMapper.readValue(anyString(), eq(JobEvent.class))).thenReturn(evt);

        listener.handleAssigned(record("{}"), ack);

        verify(maintenanceJobService, never()).markSlot(any());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("handleJobCreated creates inspection and re-publishes to job.inspection.created")
    void jobCreatedInspection() throws Exception {
        UUID inspId = UUID.randomUUID();
        UUID contractId = UUID.randomUUID();
        UUID houseId = UUID.randomUUID();

        JobCreatedEvent evt = JobCreatedEvent.builder()
                .referenceId(contractId).houseId(houseId)
                .referenceType("INSPECTION").type("CHECK_IN").build();
        InspectionDto created = new InspectionDto(inspId, houseId, contractId, null, null, null, null,
                InspectionStatus.CREATED, InspectionType.CHECK_IN, "x", null, null);

        when(objectMapper.readValue(anyString(), eq(JobCreatedEvent.class))).thenReturn(evt);
        when(inspectionJobService.createFromEvent(evt)).thenReturn(created);

        listener.handleJobCreated(record("{}"), ack);

        verify(inspectionJobService).createFromEvent(evt);
        verify(kafka).send(eq("job.inspection.created"), eq(inspId.toString()), any(JobCreatedEvent.class));
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("handleJobCreated skips non-INSPECTION events but still acks")
    void jobCreatedNonInspection() throws Exception {
        JobCreatedEvent evt = JobCreatedEvent.builder()
                .referenceType("MAINTENANCE").build();
        when(objectMapper.readValue(anyString(), eq(JobCreatedEvent.class))).thenReturn(evt);

        listener.handleJobCreated(record("{}"), ack);

        verify(inspectionJobService, never()).createFromEvent(any());
        verify(ack).acknowledge();
    }
}
