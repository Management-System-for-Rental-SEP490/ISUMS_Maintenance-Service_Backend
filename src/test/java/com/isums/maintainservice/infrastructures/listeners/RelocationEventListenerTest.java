package com.isums.maintainservice.infrastructures.listeners;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.isums.maintainservice.domains.events.RelocationReportedEvent;
import com.isums.maintainservice.domains.events.RelocationReviewedEvent;
import com.isums.maintainservice.infrastructures.abstracts.InspectionJobService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
@DisplayName("RelocationEventListener")
class RelocationEventListenerTest {

    @Mock private InspectionJobService inspectionJobService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private RelocationEventListener listener;

    @Test
    @DisplayName("reported event moves inspection to manager review")
    void reported() throws Exception {
        UUID contractId = UUID.randomUUID();
        RelocationReportedEvent event = RelocationReportedEvent.builder()
                .oldContractId(contractId)
                .build();
        when(objectMapper.readValue("reported", RelocationReportedEvent.class)).thenReturn(event);

        listener.handleRelocationReported("reported");

        verify(inspectionJobService).markPendingManagerReview(contractId);
    }

    @Test
    @DisplayName("approved review event approves inspection job")
    void approved() throws Exception {
        UUID contractId = UUID.randomUUID();
        RelocationReviewedEvent event = RelocationReviewedEvent.builder()
                .oldContractId(contractId)
                .approved(true)
                .build();
        when(objectMapper.readValue("reviewed", RelocationReviewedEvent.class)).thenReturn(event);

        listener.handleRelocationReviewed("reviewed");

        verify(inspectionJobService).markManagerReviewed(contractId, true);
    }

    @Test
    @DisplayName("rejected review event closes inspection without approval")
    void rejected() throws Exception {
        UUID contractId = UUID.randomUUID();
        RelocationReviewedEvent event = RelocationReviewedEvent.builder()
                .oldContractId(contractId)
                .approved(false)
                .build();
        when(objectMapper.readValue("reviewed", RelocationReviewedEvent.class)).thenReturn(event);

        listener.handleRelocationReviewed("reviewed");

        verify(inspectionJobService).markManagerReviewed(contractId, false);
    }

    @Test
    @DisplayName("malformed payload is skipped")
    void malformed() throws Exception {
        when(objectMapper.readValue(any(String.class), eq(RelocationReportedEvent.class)))
                .thenThrow(new JsonParseException(null, "bad"));

        listener.handleRelocationReported("bad");

        verifyNoInteractions(inspectionJobService);
    }

    @Test
    @DisplayName("missing contract id is skipped")
    void missingContractId() throws Exception {
        when(objectMapper.readValue("reported", RelocationReportedEvent.class))
                .thenReturn(RelocationReportedEvent.builder().build());

        listener.handleRelocationReported("reported");

        verify(inspectionJobService, never()).markPendingManagerReview(any());
    }

    @Test
    @DisplayName("downstream failure is rethrown for Kafka retry")
    void downstreamFailure() throws Exception {
        UUID contractId = UUID.randomUUID();
        when(objectMapper.readValue("reviewed", RelocationReviewedEvent.class))
                .thenReturn(RelocationReviewedEvent.builder()
                        .oldContractId(contractId)
                        .approved(true)
                        .build());
        doThrow(new RuntimeException("db down"))
                .when(inspectionJobService).markManagerReviewed(contractId, true);

        assertThatThrownBy(() -> listener.handleRelocationReviewed("reviewed"))
                .isInstanceOf(RuntimeException.class);
    }
}
