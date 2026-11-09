package com.finance.tx.dto;

import com.finance.tx.model.TransactionStatus;
import com.finance.tx.model.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransactionResponse(
        Long id,
        Long accountId,
        TransactionType type,
        BigDecimal amount,
        String currency,
        String description,
        TransactionStatus status,
        String idempotencyKey,
        LocalDateTime createdAt
) {
}
