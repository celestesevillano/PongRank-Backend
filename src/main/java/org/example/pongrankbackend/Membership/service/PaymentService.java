package org.example.pongrankbackend.Membership.service;

import org.example.pongrankbackend.Membership.MembershipPlan;
import org.example.pongrankbackend.Membership.dto.CreatePreferenceResponseDTO;
import org.example.pongrankbackend.Membership.dto.PaymentStatusResponseDTO;

public interface PaymentService {

    CreatePreferenceResponseDTO createPaymentPreference(Long playerId, MembershipPlan plan);

    void processWebhookNotification(String paymentId);

    // Valida la firma HMAC que MercadoPago manda en el header x-signature, para que nadie
    // pueda simular un webhook y forzar el procesamiento de un pago ajeno
    boolean isValidWebhookSignature(String xSignature, String xRequestId, String dataId);

    PaymentStatusResponseDTO getPaymentStatus(Long transactionId, Long requestingPlayerId, boolean isSystemAdmin);
}
