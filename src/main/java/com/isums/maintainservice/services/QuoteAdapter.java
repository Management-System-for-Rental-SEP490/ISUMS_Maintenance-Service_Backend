package com.isums.maintainservice.services;

import com.isums.issueservice.grpc.QuoteFullItem;
import com.isums.issueservice.grpc.QuoteFullResponse;
import com.isums.maintainservice.domains.dtos.IssueQuoteDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public final class QuoteAdapter {

    private QuoteAdapter() {}

    public static IssueQuoteDto toDto(QuoteFullResponse resp) {
        if (resp == null || !resp.getFound()) {
            return null;
        }
        List<IssueQuoteDto.QuoteItemDto> items = resp.getItemsList().stream()
                .map(QuoteAdapter::toItem)
                .collect(Collectors.toList());
        return new IssueQuoteDto(
                parseUuid(resp.getId()),
                parseUuid(resp.getReferenceId()),
                normalize(resp.getReferenceType()),
                parseUuid(resp.getStaffId()),
                parsePrice(resp.getTotalPrice()),
                resp.getIsTenantFault(),
                normalize(resp.getStatus()),
                resp.getCreatedAtEpochMilli() > 0 ? Instant.ofEpochMilli(resp.getCreatedAtEpochMilli()) : null,
                items
        );
    }

    private static IssueQuoteDto.QuoteItemDto toItem(QuoteFullItem item) {
        return new IssueQuoteDto.QuoteItemDto(
                parseUuid(item.getId()),
                normalize(item.getItemName()),
                normalize(item.getDescription()),
                parsePrice(item.getPrice())
        );
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static BigDecimal parsePrice(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value;
    }
}
