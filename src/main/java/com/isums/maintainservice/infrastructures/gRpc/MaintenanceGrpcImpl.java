package com.isums.maintainservice.infrastructures.gRpc;

import com.isums.maintainservice.domains.entities.InspectionJob;
import com.isums.maintainservice.domains.entities.MaintenanceJob;
import com.isums.maintainservice.infrastructures.repositories.InspectionJobRepository;
import com.isums.maintainservice.infrastructures.repositories.MaintenanceJobRepository;
import com.isums.maintenanceservice.grpc.GetJobRequest;
import com.isums.maintenanceservice.grpc.GetJobResponse;
import com.isums.maintenanceservice.grpc.MaintenanceServiceGrpc;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MaintenanceGrpcImpl extends MaintenanceServiceGrpc.MaintenanceServiceImplBase{
    private final MaintenanceJobRepository maintenanceJobRepository;
    private final InspectionJobRepository inspectionJobRepository;

    @Override
    @Transactional(readOnly = true)
    public void getHouseByJobId(GetJobRequest request, StreamObserver<GetJobResponse> responseObserver) {
        try {
            UUID jobId;
            try {
                jobId = UUID.fromString(request.getJobId());
            } catch (IllegalArgumentException e) {
                responseObserver.onError(
                        Status.INVALID_ARGUMENT
                                .withDescription("Invalid jobId format")
                                .asRuntimeException()
                );
                return;
            }

            UUID houseId = maintenanceJobRepository.findById(jobId)
                    .map(MaintenanceJob::getHouseId)
                    .or(() -> inspectionJobRepository.findById(jobId)
                            .map(InspectionJob::getHouseId))
                    .orElseThrow(() ->
                            Status.NOT_FOUND
                                    .withDescription("Job not found: " + jobId)
                                    .asRuntimeException()
                    );

            GetJobResponse response = GetJobResponse.newBuilder()
                    .setJobId(jobId.toString())
                    .setHouseId(houseId.toString())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception ex) {
            responseObserver.onError(
                    Status.INTERNAL.withDescription("Internal server error").withCause(ex).asRuntimeException()
            );
        }
    }
}
