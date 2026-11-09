package com.finance.tx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.tx.dto.CreateTransactionRequest;
import com.finance.tx.dto.TransactionResponse;
import com.finance.tx.exception.InsufficientFundsException;
import com.finance.tx.model.Account;
import com.finance.tx.model.IdempotencyRecord;
import com.finance.tx.model.Transaction;
import com.finance.tx.model.TransactionStatus;
import com.finance.tx.model.TransactionType;
import com.finance.tx.repository.AccountRepository;
import com.finance.tx.repository.IdempotencyRecordRepository;
import com.finance.tx.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Mock
    private AsyncNotificationService asyncNotificationService;

    private TransactionService transactionService;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(
                accountRepository, transactionRepository,
                idempotencyRecordRepository, asyncNotificationService, new ObjectMapper());
    }

    private Account accountWithBalance(String balance) {
        Account account = new Account("ACC-001", "Ada Lovelace", new BigDecimal(balance), "USD");
        account.setId(1L);
        return account;
    }

    @Test
    void creditIncreasesBalance() {
        Account account = accountWithBalance("100.00");
        when(idempotencyRecordRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            tx.setId(10L);
            return tx;
        });

        CreateTransactionRequest request = new CreateTransactionRequest(
                1L, TransactionType.CREDIT, new BigDecimal("50.00"), "USD", "Refund", "key-1");
        TransactionResponse response = transactionService.createTransaction(request);

        assertEquals(new BigDecimal("150.00"), account.getBalance());
        assertEquals(TransactionStatus.COMPLETED, response.status());
        assertEquals(new BigDecimal("50.00"), response.amount());
        verify(asyncNotificationService).sendTransactionNotification(
                10L, 1L, "CREDIT", new BigDecimal("50.00"));
    }

    @Test
    void debitWithInsufficientFundsThrowsAndDoesNotChangeBalance() {
        Account account = accountWithBalance("20.00");
        when(idempotencyRecordRepository.findByIdempotencyKey("key-2")).thenReturn(Optional.empty());
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        CreateTransactionRequest request = new CreateTransactionRequest(
                1L, TransactionType.DEBIT, new BigDecimal("100.00"), "USD", "Too big", "key-2");

        assertThrows(InsufficientFundsException.class,
                () -> transactionService.createTransaction(request));
        assertEquals(new BigDecimal("20.00"), account.getBalance());
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(idempotencyRecordRepository, never()).save(any(IdempotencyRecord.class));
        verify(asyncNotificationService, never()).sendTransactionNotification(any(), any(), any(), any());
    }

    @Test
    void duplicateIdempotencyKeyReturnsOriginalWithoutDoubleApplying() {
        Account account = accountWithBalance("100.00");
        Transaction original = new Transaction(
                account, TransactionType.CREDIT, new BigDecimal("25.00"), "USD", "Top up", "key-3");
        original.setId(7L);
        original.setStatus(TransactionStatus.COMPLETED);

        IdempotencyRecord record = new IdempotencyRecord("key-3", 7L, "{\"id\":7}");
        when(idempotencyRecordRepository.findByIdempotencyKey("key-3")).thenReturn(Optional.of(record));
        when(transactionRepository.findById(7L)).thenReturn(Optional.of(original));

        CreateTransactionRequest replay = new CreateTransactionRequest(
                1L, TransactionType.CREDIT, new BigDecimal("25.00"), "USD", "Top up", "key-3");
        TransactionResponse response = transactionService.createTransaction(replay);

        assertEquals(7L, response.id());
        assertEquals(new BigDecimal("25.00"), response.amount());
        assertEquals(new BigDecimal("100.00"), account.getBalance());
        verify(accountRepository, never()).findById(any());
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(asyncNotificationService, never()).sendTransactionNotification(any(), any(), any(), any());
    }

    @Test
    void debitReducesBalanceWhenFundsAreSufficient() {
        Account account = accountWithBalance("200.00");
        when(idempotencyRecordRepository.findByIdempotencyKey("key-4")).thenReturn(Optional.empty());
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> {
            Transaction tx = invocation.getArgument(0);
            tx.setId(11L);
            return tx;
        });

        CreateTransactionRequest request = new CreateTransactionRequest(
                1L, TransactionType.DEBIT, new BigDecimal("75.50"), "USD", "Purchase", "key-4");
        TransactionResponse response = transactionService.createTransaction(request);

        assertEquals(new BigDecimal("124.50"), account.getBalance());
        assertEquals(TransactionStatus.COMPLETED, response.status());
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertEquals(TransactionType.DEBIT, captor.getValue().getType());
    }
}
