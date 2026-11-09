package com.finance.tx.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateAccountRequest(
        @NotBlank @Size(max = 64) String accountNumber,
        @NotBlank @Size(max = 255) String ownerName,
        @DecimalMin(value = "0.00", message = "Initial balance must not be negative") BigDecimal initialBalance,
        @Size(min = 3, max = 3) String currency
) {
}
