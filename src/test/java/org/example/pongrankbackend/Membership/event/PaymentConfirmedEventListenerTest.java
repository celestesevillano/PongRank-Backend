package org.example.pongrankbackend.Membership.event;

import org.example.pongrankbackend.Membership.MembershipPlan;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.Player.repository.PlayerRepository;
import org.example.pongrankbackend.email.service.EmailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentConfirmedEventListenerTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private PaymentConfirmedEventListener listener;

    @Test
    @DisplayName("onPaymentConfirmed: envía el correo de confirmación de pago")
    void onPaymentConfirmed_SendsConfirmationEmail() {
        Player player = Player.builder().id(1L).name("Alice").email("alice@utec.edu.pe").build();
        when(playerRepository.findById(1L)).thenReturn(Optional.of(player));

        listener.onPaymentConfirmed(new PaymentConfirmedEvent(this, 1L, MembershipPlan.PRO, new BigDecimal("19.90"), "42"));

        verify(emailService).sendPaymentConfirmationEmail(player, MembershipPlan.PRO, new BigDecimal("19.90"), "42");
    }

    @Test
    @DisplayName("onPaymentConfirmed: si el jugador ya no existe, no falla ni envía nada")
    void onPaymentConfirmed_PlayerNotFound_DoesNothing() {
        when(playerRepository.findById(1L)).thenReturn(Optional.empty());

        listener.onPaymentConfirmed(new PaymentConfirmedEvent(this, 1L, MembershipPlan.PRO, new BigDecimal("19.90"), "42"));

        verify(emailService, never()).sendPaymentConfirmationEmail(any(), any(), any(), any());
    }
}
