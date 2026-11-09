package com.finance.tx.service;

import com.finance.tx.dto.AccountResponse;
import com.finance.tx.dto.CreateAccountRequest;
import com.finance.tx.exception.ResourceNotFoundException;
import com.finance.tx.model.Account;
import com.finance.tx.repository.AccountRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        if (accountRepository.existsByAccountNumber(request.accountNumber())) {
            throw new IllegalArgumentException("Account number already exists: " + request.accountNumber());
        }
        BigDecimal initialBalance = request.initialBalance() == null ? BigDecimal.ZERO : request.initialBalance();
        String currency = request.currency() == null ? "USD" : request.currency();
        Account account = new Account(request.accountNumber(), request.ownerName(), initialBalance, currency);
        return toResponse(accountRepository.save(account));
    }

    @Cacheable("accounts")
    @Transactional(readOnly = true)
    public AccountResponse getAccountById(Long id) {
        return toResponse(findAccountOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listAccounts() {
        return accountRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Account findAccountOrThrow(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + id));
    }

    static AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getOwnerName(),
                account.getBalance(),
                account.getCurrency(),
                account.getCreatedAt()
        );
    }
}
