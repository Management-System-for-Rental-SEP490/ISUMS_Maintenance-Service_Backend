package com.isums.maintainservice.domains.dtos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IssueQuoteDto(
        UUID id,
        UUID referenceId,
        String referenceType,
        UUID staffId,
        BigDecimal totalPrice,
        Boolean isTenantFault,
        String status,
        Instant createdAt,
        List<QuoteItemDto> items
) {
    public record QuoteItemDto(
            UUID id,
            String itemName,
            String description,
            BigDecimal price
    ) {}
}
