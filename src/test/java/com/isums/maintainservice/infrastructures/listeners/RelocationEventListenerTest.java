package com.isums.maintainservice.infrastructures.listeners;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.isums.maintainservice.domains.events.RelocationReportedEvent;
import com.isums.maintainservice.infrastructures.abstracts.InspectionJobService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @InjectMocks private RelocationEventListener listener;

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
    @DisplayName("happy path: marks the old contract inspection as PENDING_MANAGER_REVIEW")
    void happyPath() throws Exception {
        UUID oldContractId = UUID.randomUUID();
        when(objectMapper.readValue("v", RelocationReportedEvent.class)).thenReturn(event(oldContractId));

        listener.handleRelocationReported("v");

        verify(inspectionJobService).markPendingManagerReview(oldContractId);
    }

    @Test
    @DisplayName("skips when oldContractId is null (malformed event)")
    void missingOldContractIdSkips() throws Exception {
        when(objectMapper.readValue("v", RelocationReportedEvent.class))
                .thenReturn(event(null));

        listener.handleRelocationReported("v");

        verify(inspectionJobService, never()).markPendingManagerReview(any());
    }

    @Test
    @DisplayName("swallows null payload (no work, no NPE)")
    void nullPayload() {
        listener.handleRelocationReported(null);
        verifyNoInteractions(inspectionJobService);
    }

    @Test
    @DisplayName("swallows JSON parse failure (poison-pill — no retry)")
    void jsonFailureSkips() throws Exception {
        when(objectMapper.readValue(any(String.class), eq(RelocationReportedEvent.class)))
                .thenThrow(new JsonParseException(null, "bad"));

        listener.handleRelocationReported("v");

        verifyNoInteractions(inspectionJobService);
    }

    @Test
    @DisplayName("rethrows when markPendingManagerReview fails (so Kafka retries the message)")
    void downstreamFailureRetries() throws Exception {
        UUID oldContractId = UUID.randomUUID();
        when(objectMapper.readValue("v", RelocationReportedEvent.class)).thenReturn(event(oldContractId));
        doThrow(new RuntimeException("db down")).when(inspectionJobService).markPendingManagerReview(oldContractId);

        assertThatThrownBy(() -> listener.handleRelocationReported("v"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("proceeds when reportReason is blank — relocation should still flow")
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

        listener.handleRelocationReported("v");

        verify(inspectionJobService).markPendingManagerReview(oldContractId);
    }

    @Test
    @DisplayName("replay invokes downstream twice (idempotency is downstream's job)")
    void replayInvokesTwice() throws Exception {
        UUID oldContractId = UUID.randomUUID();
        when(objectMapper.readValue("v", RelocationReportedEvent.class)).thenReturn(event(oldContractId));

        listener.handleRelocationReported("v");
        listener.handleRelocationReported("v");

        verify(inspectionJobService, org.mockito.Mockito.times(2)).markPendingManagerReview(oldContractId);
    }
}
