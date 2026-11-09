package com.finance.tx.controller;

import com.finance.tx.dto.CreateTransactionRequest;
import com.finance.tx.dto.TransactionResponse;
import com.finance.tx.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/transactions")
    public ResponseEntity<TransactionResponse> createTransaction(
            @Valid @RequestBody CreateTransactionRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader) {
        // The Idempotency-Key header wins when present; otherwise the body's
        // idempotencyKey field is used (it is @NotBlank, so one is always set).
        String effectiveKey = idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank()
                ? idempotencyKeyHeader
                : request.idempotencyKey();
        CreateTransactionRequest effective = new CreateTransactionRequest(
                request.accountId(), request.type(), request.amount(),
                request.currency(), request.description(), effectiveKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.createTransaction(effective));
    }

    @GetMapping("/accounts/{id}/transactions")
    public ResponseEntity<Page<TransactionResponse>> listTransactions(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(transactionService.listTransactionsByAccount(id, PageRequest.of(page, size)));
    }
}
