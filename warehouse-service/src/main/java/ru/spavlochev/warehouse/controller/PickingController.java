package ru.spavlochev.warehouse.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.spavlochev.warehouse.dto.CompletePickingRequest;
import ru.spavlochev.warehouse.dto.FailPickingRequest;
import ru.spavlochev.warehouse.dto.PickingOperationResponse;
import ru.spavlochev.warehouse.dto.StartPickingRequest;
import ru.spavlochev.warehouse.service.PickingService;

@RestController
@RequestMapping("/api/v1/picking")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_WAREHOUSE_OPERATOR')")
@Slf4j
public class PickingController {

    private final PickingService pickingService;

    @PostMapping("/start")
    public ResponseEntity<PickingOperationResponse> start(@RequestBody StartPickingRequest request) {
        log.info("Starting picking for order {} and item {}", request.orderId(), request.itemSku());
        pickingService.start(request.orderId(), request.itemSku());
        return ResponseEntity.ok(new PickingOperationResponse("started"));
    }

    @PostMapping("/complete")
    public ResponseEntity<PickingOperationResponse> complete(@RequestBody CompletePickingRequest request) {
        log.info("Completing picking for order {}, tracking number {}", request.orderId(), request.trackingNumber());
        pickingService.complete(request.orderId(), request.trackingNumber());
        return ResponseEntity.ok(new PickingOperationResponse("completed"));
    }

    @PostMapping("/fail")
    public ResponseEntity<PickingOperationResponse> fail(@RequestBody FailPickingRequest request) {
        pickingService.fail(request.orderId(), request.reason());
        return ResponseEntity.ok(new PickingOperationResponse("failed"));
    }
}
