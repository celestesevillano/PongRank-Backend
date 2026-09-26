package org.example.pongrankbackend.Player.event;

import org.example.pongrankbackend.Membership.MembershipPlan;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.email.service.EmailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccountDeletedEventListenerTest {

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AccountDeletedEventListener listener;

    @Test
    @DisplayName("onAccountDeleted: envía el correo con el nombre y el email originales (antes de la mutación de borrado)")
    void onAccountDeleted_SendsEmailWithOriginalData() {
        listener.onAccountDeleted(new AccountDeletedEvent(this, "Carlos Gomez", "carlos@domain.com", MembershipPlan.PRO));

        ArgumentCaptor<Player> captor = ArgumentCaptor.forClass(Player.class);
        verify(emailService).sendAccountDeletedEmail(captor.capture(), org.mockito.ArgumentMatchers.eq(MembershipPlan.PRO));
        assertThat(captor.getValue().getName()).isEqualTo("Carlos Gomez");
        assertThat(captor.getValue().getEmail()).isEqualTo("carlos@domain.com");
    }
}
