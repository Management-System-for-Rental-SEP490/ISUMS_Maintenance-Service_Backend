package com.isums.maintainservice.infrastructures.gRpc;

import com.isums.userservice.grpc.GetUserByIdRequest;
import com.isums.userservice.grpc.GetUserIdAndRoleByKeyCloakIdRequest;
import com.isums.userservice.grpc.UserResponse;
import com.isums.userservice.grpc.UserServiceGrpc;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserClientsGrpc {
    private final UserServiceGrpc.UserServiceBlockingStub stub;

    public UserResponse getUserIdAndRoleByKeyCloakId(String keycloakId) {
        GetUserIdAndRoleByKeyCloakIdRequest req = GetUserIdAndRoleByKeyCloakIdRequest.newBuilder().setKeycloakId(keycloakId).build();
        return stub.getUserIdAndRoleByKeyCloakId(req);
    }

    public UserResponse getUser(String userId) {
        GetUserByIdRequest req = GetUserByIdRequest.newBuilder().setUserId(userId).build();
        return stub.getUserById(req);
    }
}
