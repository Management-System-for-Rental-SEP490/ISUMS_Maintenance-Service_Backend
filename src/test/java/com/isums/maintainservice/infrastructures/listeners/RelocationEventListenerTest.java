package com.isums.maintainservice.infrastructures.listeners;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.isums.maintainservice.domains.events.RelocationReportedEvent;
import com.isums.maintainservice.infrastructures.abstracts.InspectionJobService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RelocationEventListener (đổi nhà)")
class RelocationEventListenerTest {

    @Mock private InspectionJobService inspectionJobService;
    @Mock private ObjectMapper objectMapper;
    @Mock private Acknowledgment ack;

    @InjectMocks private RelocationEventListener listener;

    private final ConsumerRecord<String, String> rec =
            new ConsumerRecord<>("relocation.reported", 0, 0L, "k", "v");

    private RelocationReportedEvent event(UUID oldContractId) {
        return RelocationReportedEvent.builder()
                .relocationRequestId(UUID.randomUUID())
                .oldContractId(oldContractId)
                .oldHouseId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .staffReportedBy(UUID.randomUUID())
                .reportReason("Tường nứt + ống nước rò rỉ — không ở được")
                .reportedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("happy path: marks the old contract inspection as PENDING_MANAGER_REVIEW + acks")
    void happyPath() throws Exception {
        UUID oldContractId = UUID.randomUUID();
        when(objectMapper.readValue("v", RelocationReportedEvent.class)).thenReturn(event(oldContractId));

        listener.handleRelocationReported(rec, ack);

        verify(inspectionJobService).markPendingManagerReview(oldContractId);
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("skips + acks when oldContractId is null (malformed event from upstream)")
    void missingOldContractIdSkips() throws Exception {
        when(objectMapper.readValue("v", RelocationReportedEvent.class))
                .thenReturn(event(null));

        listener.handleRelocationReported(rec, ack);

        verify(inspectionJobService, never()).markPendingManagerReview(any());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("acks on JSON parse failure (poison-pill — no retry, no DLQ)")
    void jsonFailureSkips() throws Exception {
        when(objectMapper.readValue(any(String.class), eq(RelocationReportedEvent.class)))
                .thenThrow(new JsonParseException(null, "bad"));

        listener.handleRelocationReported(rec, ack);

        verifyNoInteractions(inspectionJobService);
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("rethrows when markPendingManagerReview fails (so Kafka retries the message)")
    void downstreamFailureRetries() throws Exception {
        UUID oldContractId = UUID.randomUUID();
        when(objectMapper.readValue("v", RelocationReportedEvent.class)).thenReturn(event(oldContractId));
        doThrow(new RuntimeException("db down")).when(inspectionJobService).markPendingManagerReview(oldContractId);

        assertThatThrownBy(() -> listener.handleRelocationReported(rec, ack))
                .isInstanceOf(RuntimeException.class);

        verify(ack, never()).acknowledge();
    }

    @Test
    @DisplayName("acks even when reportReason is blank — relocation should still flow")
    void blankReasonStillProcesses() throws Exception {
        UUID oldContractId = UUID.randomUUID();
        RelocationReportedEvent evt = RelocationReportedEvent.builder()
                .relocationRequestId(UUID.randomUUID())
                .oldContractId(oldContractId)
                .oldHouseId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .reportReason("")
                .reportedAt(Instant.now())
                .build();
        when(objectMapper.readValue("v", RelocationReportedEvent.class)).thenReturn(evt);

        listener.handleRelocationReported(rec, ack);

        verify(inspectionJobService).markPendingManagerReview(oldContractId);
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("idempotent-by-contract: handler doesn't fail when called for the same contract twice (downstream guards)")
    void replayIsAcked() throws Exception {
        UUID oldContractId = UUID.randomUUID();
        when(objectMapper.readValue("v", RelocationReportedEvent.class)).thenReturn(event(oldContractId));

        listener.handleRelocationReported(rec, ack);
        listener.handleRelocationReported(rec, ack);

        verify(inspectionJobService, org.mockito.Mockito.times(2)).markPendingManagerReview(oldContractId);
        verify(ack, org.mockito.Mockito.times(2)).acknowledge();
    }
}
