package org.example.pongrankbackend.Membership.service;

import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;
import org.example.pongrankbackend.Membership.Membership;
import org.example.pongrankbackend.Membership.MembershipPlan;
import org.example.pongrankbackend.Membership.MembershipStatus;
import org.example.pongrankbackend.Membership.PaymentStatus;
import org.example.pongrankbackend.Membership.PaymentTransaction;
import org.example.pongrankbackend.Membership.dto.CreatePreferenceResponseDTO;
import org.example.pongrankbackend.Membership.dto.PaymentStatusResponseDTO;
import org.example.pongrankbackend.Membership.repository.PaymentTransactionRepository;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.common.exception.PaymentProcessingException;
import org.example.pongrankbackend.common.exception.ResourceNotFoundException;
import org.example.pongrankbackend.common.exception.UnauthorizedActionException;
import org.example.pongrankbackend.email.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    private static final BigDecimal PRO_PRICE = new BigDecimal("19.90");
    private static final String WEBHOOK_URL = "http://localhost:8080/api/v1/payments/webhook";

    @Mock
    private MembershipService membershipService;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "webhookUrl", WEBHOOK_URL);
        ReflectionTestUtils.setField(paymentService, "proPrice", PRO_PRICE);
    }

    // ----- createPaymentPreference -----

    @Test
    @DisplayName("createPaymentPreference: rechaza FREEMIUM porque no requiere pago")
    void createPaymentPreference_Freemium_ThrowsPaymentProcessingException() {
        assertThatThrownBy(() -> paymentService.createPaymentPreference(1L, MembershipPlan.FREEMIUM))
                .isInstanceOf(PaymentProcessingException.class);

        verify(membershipService, never()).getOrCreatePendingMembership(any(), any());
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("createPaymentPreference: crea la transacción PENDING y devuelve el initPoint de MercadoPago")
    void createPaymentPreference_Premium_ReturnsInitPoint() throws MPException, MPApiException {
        Membership membership = Membership.builder().id(3L).plan(MembershipPlan.PRO).status(MembershipStatus.PENDING).build();
        when(membershipService.getOrCreatePendingMembership(1L, MembershipPlan.PRO)).thenReturn(membership);
        when(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .thenAnswer(invocation -> {
                    PaymentTransaction transaction = invocation.getArgument(0);
                    transaction.setId(42L);
                    return transaction;
                });

        Preference fakePreference = mock(Preference.class);
        when(fakePreference.getId()).thenReturn("pref-123");
        when(fakePreference.getInitPoint()).thenReturn("https://mercadopago.com/checkout/pref-123");

        try (MockedConstruction<PreferenceClient> mocked = mockConstruction(PreferenceClient.class, (mockClient, context) -> {
            try {
                when(mockClient.create(any(PreferenceRequest.class))).thenReturn(fakePreference);
            } catch (MPException | MPApiException e) {
                throw new RuntimeException(e);
            }
        })) {
            CreatePreferenceResponseDTO response = paymentService.createPaymentPreference(1L, MembershipPlan.PRO);

            assertThat(response.getInitPoint()).isEqualTo("https://mercadopago.com/checkout/pref-123");
            assertThat(response.getPreferenceId()).isEqualTo("pref-123");
            assertThat(membership.getMercadoPagoPreferenceId()).isEqualTo("pref-123");

            PreferenceClient constructedClient = mocked.constructed().get(0);
            ArgumentCaptor<PreferenceRequest> captor = ArgumentCaptor.forClass(PreferenceRequest.class);
            verify(constructedClient).create(captor.capture());

            PreferenceRequest request = captor.getValue();
            assertThat(request.getExternalReference()).isEqualTo("42");
            assertThat(request.getNotificationUrl()).isEqualTo(WEBHOOK_URL);
            assertThat(request.getItems()).hasSize(1);
            assertThat(request.getItems().get(0).getUnitPrice()).isEqualTo(PRO_PRICE);
        }
    }

    @Test
    @DisplayName("createPaymentPreference: envuelve un error de MercadoPago en PaymentProcessingException")
    void createPaymentPreference_MercadoPagoFails_WrapsInPaymentProcessingException() throws MPException, MPApiException {
        Membership membership = Membership.builder().id(3L).plan(MembershipPlan.PRO).status(MembershipStatus.PENDING).build();
        when(membershipService.getOrCreatePendingMembership(1L, MembershipPlan.PRO)).thenReturn(membership);
        when(paymentTransactionRepository.save(any(PaymentTransaction.class)))
                .thenAnswer(invocation -> {
                    PaymentTransaction transaction = invocation.getArgument(0);
                    transaction.setId(42L);
                    return transaction;
                });

        try (MockedConstruction<PreferenceClient> mocked = mockConstruction(PreferenceClient.class, (mockClient, context) -> {
            try {
                when(mockClient.create(any(PreferenceRequest.class))).thenThrow(new MPException("caído"));
            } catch (MPException | MPApiException e) {
                throw new RuntimeException(e);
            }
        })) {
            assertThatThrownBy(() -> paymentService.createPaymentPreference(1L, MembershipPlan.PRO))
                    .isInstanceOf(PaymentProcessingException.class);
        }
    }

    // ----- processWebhookNotification -----

    @Test
    @DisplayName("processWebhookNotification: pago approved activa la membresía")
    void processWebhookNotification_Approved_ActivatesMembership() throws MPException, MPApiException {
        Membership membership = Membership.builder().id(3L).status(MembershipStatus.PENDING).build();
        PaymentTransaction transaction = PaymentTransaction.builder().id(42L).membership(membership).status(PaymentStatus.PENDING).build();
        when(paymentTransactionRepository.findById(42L)).thenReturn(Optional.of(transaction));

        Payment fakePayment = mock(Payment.class);
        when(fakePayment.getExternalReference()).thenReturn("42");
        when(fakePayment.getStatus()).thenReturn("approved");

        try (MockedConstruction<PaymentClient> mocked = mockConstruction(PaymentClient.class, (mockClient, context) -> {
            try {
                when(mockClient.get(anyLong())).thenReturn(fakePayment);
            } catch (MPException | MPApiException e) {
                throw new RuntimeException(e);
            }
        })) {
            paymentService.processWebhookNotification("999");

            assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.APPROVED);
            assertThat(transaction.getMercadoPagoPaymentId()).isEqualTo("999");
            verify(membershipService).activatePaidMembership(membership);
            verify(paymentTransactionRepository).saveAndFlush(transaction);
        }
    }

    @Test
    @DisplayName("processWebhookNotification: pago rejected no activa la membresía")
    void processWebhookNotification_Rejected_DoesNotActivateMembership() throws MPException, MPApiException {
        Membership membership = Membership.builder().id(3L).status(MembershipStatus.PENDING).build();
        PaymentTransaction transaction = PaymentTransaction.builder().id(42L).membership(membership).status(PaymentStatus.PENDING).build();
        when(paymentTransactionRepository.findById(42L)).thenReturn(Optional.of(transaction));

        Payment fakePayment = mock(Payment.class);
        when(fakePayment.getExternalReference()).thenReturn("42");
        when(fakePayment.getStatus()).thenReturn("rejected");

        try (MockedConstruction<PaymentClient> mocked = mockConstruction(PaymentClient.class, (mockClient, context) -> {
            try {
                when(mockClient.get(anyLong())).thenReturn(fakePayment);
            } catch (MPException | MPApiException e) {
                throw new RuntimeException(e);
            }
        })) {
            paymentService.processWebhookNotification("999");

            assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.REJECTED);
            verify(membershipService, never()).activatePaidMembership(any());
        }
    }

    @Test
    @DisplayName("processWebhookNotification: si ya estaba APPROVED no lo reprocesa (MercadoPago puede reenviar el webhook)")
    void processWebhookNotification_AlreadyApproved_IsIdempotent() {
        Membership membership = Membership.builder().id(3L).status(MembershipStatus.ACTIVE).build();
        PaymentTransaction transaction = PaymentTransaction.builder().id(42L).membership(membership).status(PaymentStatus.APPROVED).build();
        when(paymentTransactionRepository.findById(42L)).thenReturn(Optional.of(transaction));

        try (MockedConstruction<PaymentClient> mocked = mockConstruction(PaymentClient.class, (mockClient, context) -> {
            Payment fakePayment = buildPaymentWithExternalReference("42");
            try {
                when(mockClient.get(anyLong())).thenReturn(fakePayment);
            } catch (MPException | MPApiException e) {
                throw new RuntimeException(e);
            }
        })) {
            paymentService.processWebhookNotification("999");

            verify(membershipService, never()).activatePaidMembership(any());
            verify(paymentTransactionRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("processWebhookNotification: si la membresía ya estaba activa por otra transacción (reintento de pago), no la reactiva ni reenvía el correo")
    void processWebhookNotification_MembershipAlreadyActive_DoesNotReactivateOrResendEmail() throws MPException, MPApiException {
        Membership membership = Membership.builder().id(3L).status(MembershipStatus.ACTIVE).build();
        PaymentTransaction transaction = PaymentTransaction.builder().id(42L).membership(membership).status(PaymentStatus.PENDING).build();
        when(paymentTransactionRepository.findById(42L)).thenReturn(Optional.of(transaction));
        when(paymentTransactionRepository.saveAndFlush(any(PaymentTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment fakePayment = mock(Payment.class);
        when(fakePayment.getExternalReference()).thenReturn("42");
        when(fakePayment.getStatus()).thenReturn("approved");

        try (MockedConstruction<PaymentClient> mocked = mockConstruction(PaymentClient.class, (mockClient, context) -> {
            try {
                when(mockClient.get(anyLong())).thenReturn(fakePayment);
            } catch (MPException | MPApiException e) {
                throw new RuntimeException(e);
            }
        })) {
            paymentService.processWebhookNotification("999");

            assertThat(transaction.getStatus()).isEqualTo(PaymentStatus.APPROVED);
            verify(membershipService, never()).activatePaidMembership(any());
            verify(emailService, never()).sendPaymentConfirmationEmail(any(), any(), any(), any());
        }
    }

    @Test
    @DisplayName("processWebhookNotification: si otra entrega concurrente del webhook ya actualizó la transacción, se ignora sin relanzar")
    void processWebhookNotification_ConcurrentDelivery_IsIgnored() throws MPException, MPApiException {
        Membership membership = Membership.builder().id(3L).status(MembershipStatus.PENDING).build();
        PaymentTransaction transaction = PaymentTransaction.builder().id(42L).membership(membership).status(PaymentStatus.PENDING).build();
        when(paymentTransactionRepository.findById(42L)).thenReturn(Optional.of(transaction));
        when(paymentTransactionRepository.saveAndFlush(any(PaymentTransaction.class)))
                .thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(PaymentTransaction.class, 42L));

        Payment fakePayment = mock(Payment.class);
        when(fakePayment.getExternalReference()).thenReturn("42");
        when(fakePayment.getStatus()).thenReturn("approved");

        try (MockedConstruction<PaymentClient> mocked = mockConstruction(PaymentClient.class, (mockClient, context) -> {
            try {
                when(mockClient.get(anyLong())).thenReturn(fakePayment);
            } catch (MPException | MPApiException e) {
                throw new RuntimeException(e);
            }
        })) {
            paymentService.processWebhookNotification("999");

            verify(membershipService, never()).activatePaidMembership(any());
        }
    }

    // ----- isValidWebhookSignature -----

    @Test
    @DisplayName("isValidWebhookSignature: sin secreto configurado, se omite la verificación (permite)")
    void isValidWebhookSignature_NoSecretConfigured_ReturnsTrue() {
        ReflectionTestUtils.setField(paymentService, "webhookSecret", "");

        assertThat(paymentService.isValidWebhookSignature(null, null, "999")).isTrue();
    }

    @Test
    @DisplayName("isValidWebhookSignature: firma calculada correctamente con el secreto es válida")
    void isValidWebhookSignature_ValidSignature_ReturnsTrue() throws Exception {
        ReflectionTestUtils.setField(paymentService, "webhookSecret", "mi-secreto");
        String dataId = "999";
        String requestId = "req-1";
        String ts = "1700000000";
        String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + ts + ";";

        javax.crypto.Mac hmac = javax.crypto.Mac.getInstance("HmacSHA256");
        hmac.init(new javax.crypto.spec.SecretKeySpec("mi-secreto".getBytes(), "HmacSHA256"));
        byte[] hash = hmac.doFinal(manifest.getBytes());
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) hex.append(String.format("%02x", b));

        String xSignature = "ts=" + ts + ",v1=" + hex;

        assertThat(paymentService.isValidWebhookSignature(xSignature, requestId, dataId)).isTrue();
    }

    @Test
    @DisplayName("isValidWebhookSignature: firma con secreto incorrecto es inválida")
    void isValidWebhookSignature_WrongSignature_ReturnsFalse() {
        ReflectionTestUtils.setField(paymentService, "webhookSecret", "mi-secreto");

        assertThat(paymentService.isValidWebhookSignature("ts=1700000000,v1=abc123", "req-1", "999")).isFalse();
    }

    @Test
    @DisplayName("isValidWebhookSignature: header ausente con secreto configurado es inválido")
    void isValidWebhookSignature_MissingHeader_ReturnsFalse() {
        ReflectionTestUtils.setField(paymentService, "webhookSecret", "mi-secreto");

        assertThat(paymentService.isValidWebhookSignature(null, "req-1", "999")).isFalse();
    }

    @Test
    @DisplayName("processWebhookNotification: lanza ResourceNotFoundException si la transacción no existe")
    void processWebhookNotification_TransactionNotFound_ThrowsException() {
        try (MockedConstruction<PaymentClient> mocked = mockConstruction(PaymentClient.class, (mockClient, context) -> {
            Payment fakePayment = buildPaymentWithExternalReference("999999");
            try {
                when(mockClient.get(anyLong())).thenReturn(fakePayment);
            } catch (MPException | MPApiException e) {
                throw new RuntimeException(e);
            }
        })) {
            when(paymentTransactionRepository.findById(999999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.processWebhookNotification("1"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test
    @DisplayName("processWebhookNotification: lanza PaymentProcessingException si la referencia externa no es un ID válido")
    void processWebhookNotification_InvalidExternalReference_ThrowsException() {
        try (MockedConstruction<PaymentClient> mocked = mockConstruction(PaymentClient.class, (mockClient, context) -> {
            Payment fakePayment = buildPaymentWithExternalReference("no-es-un-numero");
            try {
                when(mockClient.get(anyLong())).thenReturn(fakePayment);
            } catch (MPException | MPApiException e) {
                throw new RuntimeException(e);
            }
        })) {
            assertThatThrownBy(() -> paymentService.processWebhookNotification("1"))
                    .isInstanceOf(PaymentProcessingException.class);
        }
    }

    private Payment buildPaymentWithExternalReference(String externalReference) {
        Payment payment = mock(Payment.class);
        when(payment.getExternalReference()).thenReturn(externalReference);
        return payment;
    }

    // ----- getPaymentStatus -----

    @Test
    @DisplayName("getPaymentStatus: el dueño de la transacción puede consultarla")
    void getPaymentStatus_Owner_ReturnsStatus() {
        Player owner = Player.builder().id(1L).build();
        Membership membership = Membership.builder().player(owner).status(MembershipStatus.ACTIVE).build();
        PaymentTransaction transaction = PaymentTransaction.builder().id(42L).membership(membership).status(PaymentStatus.APPROVED).amount(PRO_PRICE).build();
        when(paymentTransactionRepository.findById(42L)).thenReturn(Optional.of(transaction));

        PaymentStatusResponseDTO response = paymentService.getPaymentStatus(42L, 1L, false);

        assertThat(response.getTransactionId()).isEqualTo(42L);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(response.getMembershipStatus()).isEqualTo(MembershipStatus.ACTIVE);
    }

    @Test
    @DisplayName("getPaymentStatus: un SYSTEM_ADMIN puede consultar la transacción de cualquier jugador")
    void getPaymentStatus_Admin_ReturnsStatus() {
        Player owner = Player.builder().id(1L).build();
        Membership membership = Membership.builder().player(owner).status(MembershipStatus.ACTIVE).build();
        PaymentTransaction transaction = PaymentTransaction.builder().id(42L).membership(membership).status(PaymentStatus.APPROVED).amount(PRO_PRICE).build();
        when(paymentTransactionRepository.findById(42L)).thenReturn(Optional.of(transaction));

        PaymentStatusResponseDTO response = paymentService.getPaymentStatus(42L, 999L, true);

        assertThat(response.getTransactionId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("getPaymentStatus: lanza UnauthorizedActionException si no es el dueño ni admin")
    void getPaymentStatus_NotOwnerNotAdmin_ThrowsException() {
        Player owner = Player.builder().id(1L).build();
        Membership membership = Membership.builder().player(owner).status(MembershipStatus.ACTIVE).build();
        PaymentTransaction transaction = PaymentTransaction.builder().id(42L).membership(membership).status(PaymentStatus.APPROVED).build();
        when(paymentTransactionRepository.findById(42L)).thenReturn(Optional.of(transaction));

        assertThatThrownBy(() -> paymentService.getPaymentStatus(42L, 2L, false))
                .isInstanceOf(UnauthorizedActionException.class);
    }

    @Test
    @DisplayName("getPaymentStatus: lanza ResourceNotFoundException si la transacción no existe")
    void getPaymentStatus_TransactionNotFound_ThrowsException() {
        when(paymentTransactionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentStatus(999L, 1L, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
