package ru.spavlochev.delivery.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.spavlochev.delivery.dto.AssignDeliveryRequest;
import ru.spavlochev.delivery.dto.DeliverRequest;
import ru.spavlochev.delivery.dto.DeliveryOperationResponse;
import ru.spavlochev.delivery.dto.FailDeliveryRequest;
import ru.spavlochev.delivery.service.DeliveryOperationService;

@RestController
@RequestMapping("/api/v1/delivery/operations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_COURIER')")
public class DeliveryOperationController {

    private final DeliveryOperationService deliveryOperationService;

    @PostMapping("/assign")
    public ResponseEntity<DeliveryOperationResponse> assign(@RequestBody AssignDeliveryRequest request) {
        deliveryOperationService.assign(request.orderId(), request.trackingNumber(), request.courierId());
        return ResponseEntity.ok(new DeliveryOperationResponse("assigned"));
    }

    @PostMapping("/deliver")
    public ResponseEntity<DeliveryOperationResponse> deliver(@RequestBody DeliverRequest request) {
        deliveryOperationService.deliver(request.orderId(), request.trackingNumber());
        return ResponseEntity.ok(new DeliveryOperationResponse("delivered"));
    }

    @PostMapping("/fail")
    public ResponseEntity<DeliveryOperationResponse> fail(@RequestBody FailDeliveryRequest request) {
        deliveryOperationService.fail(request.orderId(), request.trackingNumber(), request.reason());
        return ResponseEntity.ok(new DeliveryOperationResponse("failed"));
    }
}
