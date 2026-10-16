package ru.spavlochev.billing.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import ru.spavlochev.billing.config.UserContext;
import ru.spavlochev.billing.openapi.api.UserAccountApi;
import ru.spavlochev.billing.openapi.dto.AccountDto;
import ru.spavlochev.billing.openapi.dto.DepositRequestDto;
import ru.spavlochev.billing.service.AccountService;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class UserAccountController implements UserAccountApi {

    private final AccountService accountService;

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AccountDto> depositAccount(UUID accountId, DepositRequestDto depositRequestDto) {
        String userId = UserContext.getCurrentUserId();
        var amount = new BigDecimal(depositRequestDto.getAmount());
        var currency = depositRequestDto.getCurrency();

        var account = accountService.deposit(UUID.fromString(userId), accountId, amount, currency);
        return ResponseEntity.ok(account);
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AccountDto> getAccount() {
        String userId = UserContext.getCurrentUserId();
        var account = accountService.getAccount(UUID.fromString(userId));
        return ResponseEntity.ok(account);
    }
}
