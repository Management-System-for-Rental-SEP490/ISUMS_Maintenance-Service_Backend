package com.isums.maintainservice.infrastructures.gRpc;

import com.isums.issueservice.grpc.GetQuoteByReferenceRequest;
import com.isums.issueservice.grpc.IssueServiceGrpc;
import com.isums.issueservice.grpc.QuoteFullResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired(required = false))
@Slf4j
public class QuoteClientsGrpc {

    private final IssueServiceGrpc.IssueServiceBlockingStub issueStub;

    public QuoteFullResponse getLatestByReference(UUID referenceId, String referenceType) {
        if (issueStub == null) {
            log.warn("[QuoteGrpc] IssueServiceBlockingStub bean missing, skip remote lookup");
            return null;
        }
        try {
            GetQuoteByReferenceRequest req = GetQuoteByReferenceRequest.newBuilder()
                    .setReferenceId(referenceId.toString())
                    .setReferenceType(referenceType)
                    .build();
            QuoteFullResponse resp = issueStub.getLatestQuoteByReference(req);
            return resp.getFound() ? resp : null;
        } catch (Exception e) {
            log.warn("[QuoteGrpc] getLatestByReference failed refId={} type={}: {}",
                    referenceId, referenceType, e.getMessage());
            return null;
        }
    }
}
