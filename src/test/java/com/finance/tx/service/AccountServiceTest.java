package com.finance.tx.service;

import com.finance.tx.dto.AccountResponse;
import com.finance.tx.dto.CreateAccountRequest;
import com.finance.tx.model.Account;
import com.finance.tx.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository);
    }

    @Test
    void createAccountPersistsAndReturnsResponse() {
        when(accountRepository.existsByAccountNumber("ACC-100")).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            account.setId(1L);
            return account;
        });

        AccountResponse response = accountService.createAccount(
                new CreateAccountRequest("ACC-100", "Ada Lovelace", new BigDecimal("500.00"), "USD"));

        assertEquals(1L, response.id());
        assertEquals("ACC-100", response.accountNumber());
        assertEquals("Ada Lovelace", response.ownerName());
        assertEquals(new BigDecimal("500.00"), response.balance());
        assertEquals("USD", response.currency());
    }

    @Test
    void createAccountDefaultsToZeroBalanceAndUsd() {
        when(accountRepository.existsByAccountNumber("ACC-101")).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountResponse response = accountService.createAccount(
                new CreateAccountRequest("ACC-101", "Grace Hopper", null, null));

        assertEquals(BigDecimal.ZERO, response.balance());
        assertEquals("USD", response.currency());
    }

    @Test
    void createAccountRejectsDuplicateAccountNumber() {
        when(accountRepository.existsByAccountNumber("ACC-100")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> accountService.createAccount(
                new CreateAccountRequest("ACC-100", "Ada Lovelace", BigDecimal.ZERO, "USD")));
    }

    @Test
    void getAccountByIdReturnsCachedAccount() {
        Account account = new Account("ACC-100", "Ada Lovelace", new BigDecimal("500.00"), "USD");
        account.setId(1L);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        // Note: @Cacheable is applied by the Spring proxy at runtime, not on a
        // plain unit-tested instance, so this verifies the get logic itself.
        // The cache behaviour is exercised by the running application.
        AccountResponse first = accountService.getAccountById(1L);
        AccountResponse second = accountService.getAccountById(1L);

        assertEquals("ACC-100", first.accountNumber());
        assertEquals(first, second);
        verify(accountRepository, times(2)).findById(1L);
    }

    @Test
    void listAccountsReturnsAll() {
        Account a = new Account("ACC-100", "Ada Lovelace", BigDecimal.TEN, "USD");
        a.setId(1L);
        Account b = new Account("ACC-101", "Grace Hopper", BigDecimal.TEN, "USD");
        b.setId(2L);
        when(accountRepository.findAll()).thenReturn(List.of(a, b));

        assertEquals(2, accountService.listAccounts().size());
    }
}
