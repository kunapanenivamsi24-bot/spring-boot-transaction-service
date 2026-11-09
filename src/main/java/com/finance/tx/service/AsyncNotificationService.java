package com.finance.tx.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Demo async post-processing (notification / audit trail). In a real service
 * this would publish to a queue or call a notification provider; here it
 * simply logs, so the demo never contacts an external service.
 */
@Service
public class AsyncNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AsyncNotificationService.class);

    @Async("transactionTaskExecutor")
    public void sendTransactionNotification(Long transactionId, Long accountId,
                                            String type, BigDecimal amount) {
        log.info("Async notification: transaction {} ({} {}) for account {} processed",
                transactionId, type, amount, accountId);
    }
}
