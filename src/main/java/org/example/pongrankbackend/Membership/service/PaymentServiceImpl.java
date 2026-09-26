package org.example.pongrankbackend.Membership.service;

import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;
import org.example.pongrankbackend.Membership.Membership;
import org.example.pongrankbackend.Membership.MembershipPlan;
import org.example.pongrankbackend.Membership.PaymentStatus;
import org.example.pongrankbackend.Membership.PaymentTransaction;
import org.example.pongrankbackend.Membership.dto.CreatePreferenceResponseDTO;
import org.example.pongrankbackend.Membership.dto.PaymentStatusResponseDTO;
import org.example.pongrankbackend.Membership.repository.PaymentTransactionRepository;
import org.example.pongrankbackend.common.exception.PaymentProcessingException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.example.pongrankbackend.common.exception.UnauthorizedActionException;
import org.example.pongrankbackend.email.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final MembershipService membershipService;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final EmailService emailService;

    @Value("${mercadopago.webhook-url}")
    private String webhookUrl;

    // Secreto de la firma del webhook, configurado en el panel de MercadoPago (Notificaciones -> Webhooks).
    // Vacío en dev/local si no se configuró; en ese caso se loguea advertencia y no se bloquea la notificación.
    @Value("${mercadopago.webhook-secret:}")
    private String webhookSecret;

    @Value("${membership.basic.price}")
    private BigDecimal basicPrice;

    @Value("${membership.pro.price}")
    private BigDecimal proPrice;

    @Value("${membership.enterprise.price}")
    private BigDecimal enterprisePrice;

    public PaymentServiceImpl(MembershipService membershipService,
                               PaymentTransactionRepository paymentTransactionRepository,
                               EmailService emailService) {
        this.membershipService = membershipService;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.emailService = emailService;
    }

    @Override
    @Transactional
    public CreatePreferenceResponseDTO createPaymentPreference(Long playerId, MembershipPlan plan) {
        // FREEMIUM no cobra nada, no tiene sentido mandarlo a MercadoPago
        if (plan == MembershipPlan.FREEMIUM) {
            throw new PaymentProcessingException("El plan FREEMIUM no requiere generar un pago");
        }

        Membership membership = membershipService.getOrCreatePendingMembership(playerId, plan);

        PaymentTransaction transaction = paymentTransactionRepository.save(
                PaymentTransaction.builder()
                        .membership(membership)
                        .status(PaymentStatus.PENDING)
                        .amount(priceFor(plan))
                        .build()
        );

        Preference preference = createPreferenceInMercadoPago(transaction, plan);
        membership.setMercadoPagoPreferenceId(preference.getId());

        return CreatePreferenceResponseDTO.builder()
                .initPoint(preference.getInitPoint())
                .preferenceId(preference.getId())
                .build();
    }

    private BigDecimal priceFor(MembershipPlan plan) {
        return switch (plan) {
            case BASIC -> basicPrice;
            case PRO -> proPrice;
            case ENTERPRISE -> enterprisePrice;
            case FREEMIUM -> throw new PaymentProcessingException("El plan FREEMIUM no requiere generar un pago");
        };
    }

    private Preference createPreferenceInMercadoPago(PaymentTransaction transaction, MembershipPlan plan) {
        PreferenceItemRequest item = PreferenceItemRequest.builder()
                .title("Membresía PongRank " + plan.name())
                .quantity(1)
                .unitPrice(transaction.getAmount())
                .currencyId("PEN")
                .build();

        // externalReference es cómo recuperamos la transacción cuando llegue el webhook
        PreferenceRequest request = PreferenceRequest.builder()
                .items(List.of(item))
                .externalReference(transaction.getId().toString())
                .notificationUrl(webhookUrl)
                .build();

        try {
            return new PreferenceClient().create(request);
        } catch (MPApiException | MPException e) {
            throw new PaymentProcessingException("No se pudo crear la preferencia de pago en MercadoPago: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void processWebhookNotification(String paymentId) {
        Payment payment = fetchPaymentFromMercadoPago(paymentId);
        Long transactionId = parseExternalReference(payment.getExternalReference());

        PaymentTransaction transaction = paymentTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transacción no encontrada con ID: " + transactionId));

        // MercadoPago puede reenviar el mismo webhook más de una vez
        if (transaction.getStatus() == PaymentStatus.APPROVED) {
            return;
        }

        transaction.setMercadoPagoPaymentId(paymentId);
        transaction.setStatus(mapMercadoPagoStatus(payment.getStatus()));
        try {
            paymentTransactionRepository.saveAndFlush(transaction);
        } catch (ObjectOptimisticLockingFailureException e) {
            // Otra entrega concurrente del mismo webhook ya está procesando esta transacción; no hay nada más que hacer
            log.info("Notificación de MercadoPago duplicada/concurrente para la transacción {}, se ignora", transactionId);
            return;
        }

        if (transaction.getStatus() == PaymentStatus.APPROVED) {
            Membership membership = transaction.getMembership();
            membershipService.activatePaidMembership(membership);
            emailService.sendPaymentConfirmationEmail(
                    membership.getPlayer(), membership.getPlan(), transaction.getAmount(), transaction.getId().toString());
        }
    }

    @Override
    public boolean isValidWebhookSignature(String xSignature, String xRequestId, String dataId) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("mercadopago.webhook-secret no está configurado; se omite la verificación de firma del webhook");
            return true;
        }
        if (xSignature == null || xSignature.isBlank() || dataId == null || dataId.isBlank()) {
            return false;
        }

        Map<String, String> parts = new HashMap<>();
        for (String part : xSignature.split(",")) {
            String[] keyValue = part.split("=", 2);
            if (keyValue.length == 2) {
                parts.put(keyValue[0].trim(), keyValue[1].trim());
            }
        }
        String ts = parts.get("ts");
        String v1 = parts.get("v1");
        if (ts == null || v1 == null) {
            return false;
        }

        String manifest = "id:" + dataId.toLowerCase() + ";" +
                "request-id:" + (xRequestId == null ? "" : xRequestId) + ";" +
                "ts:" + ts + ";";

        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = hmac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString().equals(v1);
        } catch (Exception e) {
            log.error("Error validando la firma del webhook de MercadoPago", e);
            return false;
        }
    }

    private Payment fetchPaymentFromMercadoPago(String paymentId) {
        try {
            return new PaymentClient().get(Long.parseLong(paymentId));
        } catch (NumberFormatException e) {
            throw new PaymentProcessingException("El paymentId recibido no es válido: " + paymentId);
        } catch (MPApiException | MPException e) {
            throw new PaymentProcessingException("No se pudo consultar el pago en MercadoPago: " + e.getMessage());
        }
    }

    private Long parseExternalReference(String externalReference) {
        try {
            return Long.parseLong(externalReference);
        } catch (NumberFormatException | NullPointerException e) {
            throw new PaymentProcessingException("El pago de MercadoPago no trae una referencia de transacción válida");
        }
    }

    private PaymentStatus mapMercadoPagoStatus(String mpStatus) {
        return switch (mpStatus) {
            case "approved" -> PaymentStatus.APPROVED;
            case "rejected", "cancelled" -> PaymentStatus.REJECTED;
            default -> PaymentStatus.PENDING;
        };
    }

    @Override
    public PaymentStatusResponseDTO getPaymentStatus(Long transactionId, Long requestingPlayerId, boolean isSystemAdmin) {
        PaymentTransaction transaction = paymentTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transacción no encontrada con ID: " + transactionId));

        Long ownerId = transaction.getMembership().getPlayer().getId();
        if (!isSystemAdmin && !ownerId.equals(requestingPlayerId)) {
            throw new UnauthorizedActionException("Solo puedes consultar el estado de tus propias transacciones");
        }

        return PaymentStatusResponseDTO.builder()
                .transactionId(transaction.getId())
                .status(transaction.getStatus())
                .amount(transaction.getAmount())
                .membershipStatus(transaction.getMembership().getStatus())
                .build();
    }
}
