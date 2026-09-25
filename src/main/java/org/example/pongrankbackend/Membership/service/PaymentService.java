package org.example.pongrankbackend.Membership.service;

import org.example.pongrankbackend.Membership.MembershipPlan;
import org.example.pongrankbackend.Membership.dto.CreatePreferenceResponseDTO;
import org.example.pongrankbackend.Membership.dto.PaymentStatusResponseDTO;

public interface PaymentService {

    CreatePreferenceResponseDTO createPaymentPreference(Long playerId, MembershipPlan plan);

    void processWebhookNotification(String paymentId);

    PaymentStatusResponseDTO getPaymentStatus(Long transactionId, Long requestingPlayerId, boolean isSystemAdmin);
}
