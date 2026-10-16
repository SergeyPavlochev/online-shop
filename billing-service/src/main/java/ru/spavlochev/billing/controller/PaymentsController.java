package ru.spavlochev.billing.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import ru.spavlochev.billing.config.UserContext;
import ru.spavlochev.billing.openapi.api.PaymentsApi;
import ru.spavlochev.billing.openapi.dto.PaymentCancelRequestDto;
import ru.spavlochev.billing.openapi.dto.PaymentCancelResponseDto;
import ru.spavlochev.billing.openapi.dto.PaymentConfirmRequestDto;
import ru.spavlochev.billing.openapi.dto.PaymentConfirmResponseDto;
import ru.spavlochev.billing.openapi.dto.PaymentReserveRequestDto;
import ru.spavlochev.billing.openapi.dto.PaymentReserveResponseDto;
import ru.spavlochev.billing.service.PaymentsService;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PaymentsController implements PaymentsApi {

    private final PaymentsService paymentsService;

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PaymentReserveResponseDto> reserve(PaymentReserveRequestDto dto) {
        String userId = UserContext.getCurrentUserId();
        paymentsService.reserve(UUID.fromString(userId), dto.getOrderId(), dto.getAmount(), dto.getCurrency());
        return ResponseEntity.ok(new PaymentReserveResponseDto("reserved"));
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PaymentConfirmResponseDto> confirm(PaymentConfirmRequestDto dto) {
        paymentsService.confirm(dto.getOrderId());
        return ResponseEntity.ok(new PaymentConfirmResponseDto("confirmed"));
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PaymentCancelResponseDto> cancel(PaymentCancelRequestDto dto) {
        paymentsService.cancel(dto.getOrderId());
        return ResponseEntity.ok(new PaymentCancelResponseDto("cancelled"));
    }
}
