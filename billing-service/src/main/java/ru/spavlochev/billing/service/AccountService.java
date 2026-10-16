package ru.spavlochev.billing.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.spavlochev.billing.entity.Account;
import ru.spavlochev.billing.openapi.dto.AccountDto;
import ru.spavlochev.billing.repository.AccountRepository;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private static final String DEFAULT_CURRENCY = "RUB";

    private final AccountRepository accountRepository;
    private final OutboxService outboxService;

    @Transactional
    public void createAccount(UUID userId) {
        log.info("Creating account for userId={}", userId);

        // Проверяем, не создан ли уже аккаунт
        if (accountRepository.findByUserId(userId).isPresent()) {
            log.warn("Account already exists for userId={}", userId);
            accountRepository.findByUserId(userId).get();
            return;
        }

        var account = accountRepository.save(Account.builder()
                .userId(userId)
                .currency(DEFAULT_CURRENCY)
                .balance(BigDecimal.ZERO)
                .build());
        outboxService.saveEvent(
                "UserAccountCreated",
                account.getId(),
                "Account",
                Map.of("userId", userId,
                        "accountId", account.getId())
        );

        log.info("Account created: accountId={}, userId={}", account.getId(), userId);
    }

    @Transactional
    public AccountDto deposit(UUID userId, UUID accountId, BigDecimal amount, String currency) {
        log.info("Depositing {} {} to accountId={}", amount, currency, accountId);

        Account account = accountRepository.findById(accountId)
                .filter(acc -> userId.equals(acc.getUserId()))
                .orElseThrow(() -> new EntityNotFoundException("Account not found: " + accountId));

        if (!account.getCurrency().equals(currency)) {
            throw new IllegalArgumentException("Currency mismatch: account=" + account.getCurrency() + ", deposit=" + currency);
        }

        account.setBalance(account.getBalance().add(amount));
        account = accountRepository.save(account);

        log.info("Deposit successful: accountId={}, newBalance={}", accountId, account.getBalance());
        var accountDto = new AccountDto()
                .id(account.getId())
                .userId(account.getUserId())
                .balance(account.getBalance().toString())
                .currency(account.getCurrency());
        outboxService.saveEvent(
                "UserAccountDeposited",
                userId,
                "Account",
                Map.of("userId", userId,
                        "accountId", accountId,
                        "amount", amount,
                        "currency", currency));
        return accountDto;
    }

    @Transactional(readOnly = true)
    public AccountDto getAccount(UUID userId) {
        return accountRepository.findByUserId(userId)
                .map(acc -> new AccountDto()
                        .id(acc.getId())
                        .userId(acc.getUserId())
                        .balance(acc.getBalance().toString())
                        .currency(acc.getCurrency()))
                .orElseThrow(() -> new EntityNotFoundException("Account not found"));
    }
}
