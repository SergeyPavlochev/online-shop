package ru.spavlochev.billing.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
            log.warn("Cannot confirm hold with status {} for orderId={}", hold.getStatus(), orderId);
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
                    Map.of("orderId", orderId.toString(),
                            "userId", hold.getUserId().toString(),
                            "amount", hold.getAmount().toPlainString(),
                            "currency", hold.getCurrency())
            );

            log.info("Payment confirmed for orderId={}, amount={}", orderId, hold.getAmount());
        } catch (Exception e) {
            outboxService.saveEvent(
                    "OrderPaymentFailed",
                    orderId,
                    "Order",
                    Map.of("orderId", orderId.toString(),
                            "userId", hold.getUserId().toString(),
                            "amount", hold.getAmount().toPlainString(),
                            "currency", hold.getCurrency(),
                            "reason", e.getMessage()));
            // Re-throw to trigger transaction rollback — otherwise partial state is committed
            throw e;
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

    @Transactional
    public void refund(UUID orderId) {
        PaymentHold hold = paymentHoldRepository.findByOrderId(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Hold not found for orderId: " + orderId));

        if (hold.getStatus() != PaymentHold.HoldStatus.CONFIRMED) {
            log.warn("Cannot refund hold with status {} for orderId={}", hold.getStatus(), orderId);
            return;
        }

        Account account = accountRepository.findByUserIdForUpdate(hold.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("Account not found"));

        // Возвращаем деньги на счёт
        account.setBalance(account.getBalance().add(hold.getAmount()));
        accountRepository.save(account);

        hold.setStatus(PaymentHold.HoldStatus.REFUNDED);
        paymentHoldRepository.save(hold);

        outboxService.saveEvent(
                "OrderPaymentRefunded",
                orderId,
                "Order",
                Map.of("orderId", orderId.toString(),
                        "userId", hold.getUserId().toString(),
                        "amount", hold.getAmount().toPlainString(),
                        "currency", hold.getCurrency())
        );

        log.info("Payment refunded for orderId={}, amount={}", orderId, hold.getAmount());
    }
}
