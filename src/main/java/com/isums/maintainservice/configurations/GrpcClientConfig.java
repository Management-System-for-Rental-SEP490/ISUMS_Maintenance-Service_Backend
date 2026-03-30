package com.isums.maintainservice.configurations;

import com.isums.houseservice.grpc.HouseServiceGrpc;
import com.isums.userservice.grpc.UserServiceGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration
public class GrpcClientConfig {
    @Bean
    UserServiceGrpc.UserServiceBlockingStub userStub(GrpcChannelFactory channels , GrpcTokenInterceptor tokenInterceptor) {
        return UserServiceGrpc.newBlockingStub(channels.createChannel("user"))
                .withInterceptors(tokenInterceptor);
    }

    @Bean
    HouseServiceGrpc.HouseServiceBlockingStub houseStub(GrpcChannelFactory channels,GrpcTokenInterceptor tokenInterceptor
    ) {
        return HouseServiceGrpc.newBlockingStub(channels.createChannel("house"))
                .withInterceptors(tokenInterceptor);
    }
}
