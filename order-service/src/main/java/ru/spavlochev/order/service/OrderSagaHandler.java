package ru.spavlochev.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import ru.spavlochev.order.entity.OrderSagaState;
import ru.spavlochev.order.repository.OrderSagaStateRepository;

import java.time.LocalDateTime;
import java.util.List;

import static ru.spavlochev.order.entity.OrderSagaState.SagaStatus.READY_TO_START;
import static ru.spavlochev.order.entity.OrderSagaState.SagaStatus.STARTED;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderSagaHandler {

    private static final Integer BATCH_SIZE = 50;
    private static final Pageable PAGE_REQUEST = PageRequest.of(0, BATCH_SIZE);

    private final OrderSagaStateRepository sagaStateRepository;
    private final OrderSagaOrchestrator orderSagaOrchestrator;
    private final TransactionExecutor transactionExecutor;

    @Scheduled(cron = "*/1 * * * * *")
    public void startOrderSaga() {
        List<OrderSagaState> startedSagaStates =
                transactionExecutor.execInTransaction(() -> {
                    var readySagaStates = sagaStateRepository.findAllByStatusOrderByUpdatedAtAsc(READY_TO_START, PAGE_REQUEST);
                    if (readySagaStates.isEmpty()) {
                        return List.of();
                    }
                    log.info("Processing ready order saga states");
                    readySagaStates.forEach(state -> {
                        state.setStatus(STARTED);
                        state.setStartedAt(LocalDateTime.now());
                    });
                    return sagaStateRepository.saveAll(readySagaStates);
                });

        for (var orderSagaState : startedSagaStates) {
            transactionExecutor.execInTransaction(() -> orderSagaOrchestrator.beginOrderSaga(orderSagaState));
        }
    }

    // Здесь будет логика возобновления зависших саг
    // Сага считается зависшей, если ее время последнего обновления не менялось 5-10 мин (настройка)
    public void resumeSuspendedSagas() {
        // to be done later
    }
}
