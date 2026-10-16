package ru.spavlochev.billing.service;

import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

@Component
public class TransactionExecutor {

    @Transactional(propagation = Propagation.REQUIRED)
    public void execInTransaction(@Nonnull Runnable action) {
        action.run();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public <T> T execInTransaction(@Nonnull Supplier<T> supplier) {
        return supplier.get();
    }
}
