package org.example.pongrankbackend.Membership.controller;

import jakarta.validation.Valid;
import org.example.pongrankbackend.Membership.dto.CreatePreferenceRequestDTO;
import org.example.pongrankbackend.Membership.dto.CreatePreferenceResponseDTO;
import org.example.pongrankbackend.Membership.dto.MercadoPagoWebhookPayloadDTO;
import org.example.pongrankbackend.Membership.dto.PaymentStatusResponseDTO;
import org.example.pongrankbackend.Membership.service.PaymentService;
import org.example.pongrankbackend.security.CustomUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// Nota: a diferencia de otros controllers del proyecto, este NO lleva @PreAuthorize a nivel de
// clase porque mezcla un endpoint público (el webhook, MercadoPago no manda JWT) con dos
// protegidos. Poner el @PreAuthorize por método evita el lío de tener que "revertir" una
// restricción de clase para el endpoint público.
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/create-preference")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CreatePreferenceResponseDTO> createPreference(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody CreatePreferenceRequestDTO dto) {
        CreatePreferenceResponseDTO response = paymentService.createPaymentPreference(currentUser.getId(), dto.getPlan());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestBody MercadoPagoWebhookPayloadDTO payload,
            @RequestHeader(value = "x-signature", required = false) String xSignature,
            @RequestHeader(value = "x-request-id", required = false) String xRequestId) {
        if ("payment".equals(payload.getType()) && payload.getData() != null) {
            String dataId = payload.getData().getId();
            if (!paymentService.isValidWebhookSignature(xSignature, xRequestId, dataId)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            paymentService.processWebhookNotification(dataId);
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/status/{transactionId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PaymentStatusResponseDTO> getStatus(
            @PathVariable Long transactionId,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        boolean isSystemAdmin = currentUser.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SYSTEM_ADMIN"));
        PaymentStatusResponseDTO response = paymentService.getPaymentStatus(transactionId, currentUser.getId(), isSystemAdmin);
        return ResponseEntity.ok(response);
    }
}
