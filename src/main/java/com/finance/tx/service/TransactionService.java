package com.finance.tx.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.tx.dto.CreateTransactionRequest;
import com.finance.tx.dto.TransactionResponse;
import com.finance.tx.exception.InsufficientFundsException;
import com.finance.tx.exception.ResourceNotFoundException;
import com.finance.tx.model.Account;
import com.finance.tx.model.IdempotencyRecord;
import com.finance.tx.model.Transaction;
import com.finance.tx.model.TransactionStatus;
import com.finance.tx.model.TransactionType;
import com.finance.tx.repository.AccountRepository;
import com.finance.tx.repository.IdempotencyRecordRepository;
import com.finance.tx.repository.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final AsyncNotificationService asyncNotificationService;
    private final ObjectMapper objectMapper;

    public TransactionService(AccountRepository accountRepository,
                              TransactionRepository transactionRepository,
                              IdempotencyRecordRepository idempotencyRecordRepository,
                              AsyncNotificationService asyncNotificationService,
                              ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.asyncNotificationService = asyncNotificationService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TransactionResponse createTransaction(CreateTransactionRequest request) {
        // (a) Idempotent replay: a previously processed key returns the original result.
        Optional<IdempotencyRecord> existing = idempotencyRecordRepository
                .findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent() && existing.get().getTransactionId() != null) {
            return transactionRepository.findById(existing.get().getTransactionId())
                    .map(this::toResponse)
                    .orElseThrow(() -> new IllegalStateException(
                            "Idempotency record exists but linked transaction is missing for key: "
                                    + request.idempotencyKey()));
        }

        // (b) Load the account.
        Account account = accountRepository.findById(request.accountId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account not found with id: " + request.accountId()));

        // (c) For debits, verify sufficient funds.
        if (request.type() == TransactionType.DEBIT
                && account.getBalance().compareTo(request.amount()) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds: balance " + account.getBalance()
                            + " is less than debit amount " + request.amount());
        }

        // (d) Apply the balance change and persist the transaction as COMPLETED.
        BigDecimal newBalance = request.type() == TransactionType.CREDIT
                ? account.getBalance().add(request.amount())
                : account.getBalance().subtract(request.amount());
        account.setBalance(newBalance);
        accountRepository.save(account);

        String currency = request.currency() == null ? account.getCurrency() : request.currency();
        Transaction transaction = new Transaction(
                account, request.type(), request.amount(), currency,
                request.description(), request.idempotencyKey());
        transaction.setStatus(TransactionStatus.COMPLETED);
        Transaction saved = transactionRepository.save(transaction);

        TransactionResponse response = toResponse(saved);
        idempotencyRecordRepository.save(new IdempotencyRecord(
                request.idempotencyKey(), saved.getId(), toJson(response)));

        // (e) Async post-processing (notification / audit), fire-and-forget.
        asyncNotificationService.sendTransactionNotification(
                saved.getId(), account.getId(),
                saved.getType().name(), saved.getAmount());

        return response;
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> listTransactionsByAccount(Long accountId, Pageable pageable) {
        if (!accountRepository.existsById(accountId)) {
            throw new ResourceNotFoundException("Account not found with id: " + accountId);
        }
        return transactionRepository.findByAccountId(accountId, pageable).map(this::toResponse);
    }

    private TransactionResponse toResponse(Transaction tx) {
        return new TransactionResponse(
                tx.getId(),
                tx.getAccount().getId(),
                tx.getType(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getDescription(),
                tx.getStatus(),
                tx.getIdempotencyKey(),
                tx.getCreatedAt()
        );
    }

    private String toJson(TransactionResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            return "{\"id\":" + response.id() + "}";
        }
    }
}
