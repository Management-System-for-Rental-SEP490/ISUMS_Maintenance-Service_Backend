package com.isums.maintainservice.infrastructures.gRpc;

import com.isums.maintainservice.domains.entities.InspectionJob;
import com.isums.maintainservice.domains.entities.MaintenanceJob;
import com.isums.maintainservice.infrastructures.repositories.InspectionJobRepository;
import com.isums.maintainservice.infrastructures.repositories.MaintenanceJobRepository;
import com.isums.maintenanceservice.grpc.GetJobRequest;
import com.isums.maintenanceservice.grpc.GetJobResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MaintenanceGrpcImpl")
class MaintenanceGrpcImplTest {

    @Mock private MaintenanceJobRepository maintenanceJobRepository;
    @Mock private InspectionJobRepository inspectionJobRepository;
    @Mock private StreamObserver<GetJobResponse> observer;

    @InjectMocks private MaintenanceGrpcImpl impl;

    private UUID jobId, houseId;

    @BeforeEach
    void setUp() {
        jobId = UUID.randomUUID();
        houseId = UUID.randomUUID();
    }

    @Test
    @DisplayName("returns houseId from MaintenanceJob when found")
    void fromMaintenance() {
        when(maintenanceJobRepository.findById(jobId))
                .thenReturn(Optional.of(MaintenanceJob.builder().id(jobId).houseId(houseId).build()));

        impl.getHouseByJobId(
                GetJobRequest.newBuilder().setJobId(jobId.toString()).build(), observer);

        ArgumentCaptor<GetJobResponse> cap = ArgumentCaptor.forClass(GetJobResponse.class);
        verify(observer).onNext(cap.capture());
        verify(observer).onCompleted();
        assertThat(cap.getValue().getHouseId()).isEqualTo(houseId.toString());
        verify(inspectionJobRepository, never()).findById(jobId);
    }

    @Test
    @DisplayName("falls back to InspectionJob when MaintenanceJob missing")
    void fromInspection() {
        when(maintenanceJobRepository.findById(jobId)).thenReturn(Optional.empty());
        when(inspectionJobRepository.findById(jobId))
                .thenReturn(Optional.of(InspectionJob.builder().id(jobId).houseId(houseId).build()));

        impl.getHouseByJobId(
                GetJobRequest.newBuilder().setJobId(jobId.toString()).build(), observer);

        ArgumentCaptor<GetJobResponse> cap = ArgumentCaptor.forClass(GetJobResponse.class);
        verify(observer).onNext(cap.capture());
        assertThat(cap.getValue().getHouseId()).isEqualTo(houseId.toString());
    }

    @Test
    @DisplayName("signals INVALID_ARGUMENT when jobId is not a UUID")
    void badUuid() {
        impl.getHouseByJobId(
                GetJobRequest.newBuilder().setJobId("not-a-uuid").build(), observer);

        ArgumentCaptor<Throwable> errCap = ArgumentCaptor.forClass(Throwable.class);
        verify(observer).onError(errCap.capture());
        StatusRuntimeException err = (StatusRuntimeException) errCap.getValue();
        assertThat(err.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
        verify(observer, never()).onCompleted();
    }

    @Test
    @DisplayName("signals INTERNAL when job absent in both tables")
    void notFound() {
        when(maintenanceJobRepository.findById(jobId)).thenReturn(Optional.empty());
        when(inspectionJobRepository.findById(jobId)).thenReturn(Optional.empty());

        impl.getHouseByJobId(
                GetJobRequest.newBuilder().setJobId(jobId.toString()).build(), observer);

        ArgumentCaptor<Throwable> errCap = ArgumentCaptor.forClass(Throwable.class);
        verify(observer).onError(errCap.capture());
        StatusRuntimeException err = (StatusRuntimeException) errCap.getValue();
        assertThat(err.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
    }
}
