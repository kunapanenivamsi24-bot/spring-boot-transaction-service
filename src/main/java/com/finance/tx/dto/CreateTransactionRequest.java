package com.finance.tx.dto;

import com.finance.tx.model.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateTransactionRequest(
        @NotNull Long accountId,
        @NotNull TransactionType type,
        @NotNull @DecimalMin(value = "0.01", message = "Amount must be positive") BigDecimal amount,
        @Size(min = 3, max = 3) String currency,
        @Size(max = 500) String description,
        @NotBlank @Size(max = 128) String idempotencyKey
) {
}
