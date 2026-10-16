package ru.spavlochev.billing.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.spavlochev.billing.entity.Account;
import ru.spavlochev.billing.entity.PaymentHold;
import ru.spavlochev.billing.repository.AccountRepository;
import ru.spavlochev.billing.repository.PaymentHoldRepository;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentsService {

    private final AccountRepository accountRepository;
    private final PaymentHoldRepository paymentHoldRepository;
    private final OutboxService outboxService;

    @Transactional
    public void reserve(UUID userId, UUID orderId, BigDecimal amount, String currency) {
        Account account = accountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found"));

        // Проверяем ДОСТУПНЫЙ баланс (с учетом уже существующих холдов)
        BigDecimal available = account.getBalance().subtract(account.getReservedBalance());
        if (available.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient funds");
        }

        // Увеличиваем зарезервированную сумму
        account.setReservedBalance(account.getReservedBalance().add(amount));
        accountRepository.save(account);

        // Сохраняем запись о холде
        PaymentHold hold = PaymentHold.builder()
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .currency(currency)
                .status(PaymentHold.HoldStatus.RESERVED)
                .build();
        paymentHoldRepository.save(hold);
    }

    @Transactional
    public void confirm(UUID orderId) {
        PaymentHold hold = paymentHoldRepository.findByOrderId(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Hold not found"));

        if (hold.getStatus() != PaymentHold.HoldStatus.RESERVED) {
            return;
        }

        try {
            Account account = accountRepository.findByUserIdForUpdate(hold.getUserId())
                    .orElseThrow(() -> new EntityNotFoundException("Account not found"));

            // Превращаем холд в реальное списание
            account.setBalance(account.getBalance().subtract(hold.getAmount()));
            account.setReservedBalance(account.getReservedBalance().subtract(hold.getAmount()));
            accountRepository.save(account);

            hold.setStatus(PaymentHold.HoldStatus.CONFIRMED);
            paymentHoldRepository.save(hold);

            outboxService.saveEvent(
                    "OrderPaymentCompleted",
                    orderId,
                    "Order",
                    Map.of("orderId", orderId,
                            "userId", hold.getUserId(),
                            "amount", hold.getAmount(),
                            "currency", hold.getCurrency())
            );
        } catch (Exception e) {
            outboxService.saveEvent(
                    "OrderPaymentFailed",
                    orderId,
                    "Order",
                    Map.of("orderId", orderId,
                            "userId", hold.getUserId(),
                            "amount", hold.getAmount(),
                            "currency", hold.getCurrency(),
                            "reason", e.getMessage()));
        }
    }

    @Transactional
    public void cancel(UUID orderId) {
        PaymentHold hold = paymentHoldRepository.findByOrderId(orderId).orElse(null);
        if (hold == null || hold.getStatus() != PaymentHold.HoldStatus.RESERVED) {
            return;
        }

        Account account = accountRepository.findByUserIdForUpdate(hold.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("Account not found"));

        // Освобождаем холд
        account.setReservedBalance(account.getReservedBalance().subtract(hold.getAmount()));
        accountRepository.save(account);

        hold.setStatus(PaymentHold.HoldStatus.CANCELLED);
        paymentHoldRepository.save(hold);
    }
}
