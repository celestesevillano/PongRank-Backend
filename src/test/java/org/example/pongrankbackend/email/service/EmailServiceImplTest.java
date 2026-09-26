package org.example.pongrankbackend.email.service;

import jakarta.mail.internet.MimeMessage;
import org.example.pongrankbackend.Membership.MembershipPlan;
import org.example.pongrankbackend.Player.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceImplTest {

    @Mock
    private JavaMailSender mailSender;

    private SpringTemplateEngine templateEngine;

    @InjectMocks
    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");

        templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);

        emailService = new EmailServiceImpl(mailSender, templateEngine);
        ReflectionTestUtils.setField(emailService, "fromAddress", "no-reply@pongrank.app");
        ReflectionTestUtils.setField(emailService, "frontendUrl", "http://localhost:5173");
        ReflectionTestUtils.setField(emailService, "backendUrl", "http://localhost:8080");
    }

    private Player player() {
        return Player.builder().id(1L).name("Adriana").email("adriana@utec.edu.pe").build();
    }

    private MimeMessage realMimeMessage() {
        return new MimeMessage(jakarta.mail.Session.getDefaultInstance(new Properties()));
    }

    @Test
    @DisplayName("sendWelcomeEmail: renderiza la plantilla y envía el correo")
    void sendWelcomeEmail_Success_SendsMessage() {
        when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());

        emailService.sendWelcomeEmail(player());

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
    }

    @Test
    @DisplayName("sendPasswordResetEmail: renderiza la plantilla y envía el correo")
    void sendPasswordResetEmail_Success_SendsMessage() {
        when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());

        emailService.sendPasswordResetEmail(player(), "http://localhost:5173/reset-password?token=abc", 30);

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendPaymentConfirmationEmail: renderiza la plantilla y envía el correo")
    void sendPaymentConfirmationEmail_Success_SendsMessage() {
        when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());

        emailService.sendPaymentConfirmationEmail(player(), MembershipPlan.PRO, new BigDecimal("19.90"), "TXN-42");

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("sendWelcomeEmail: propaga la excepción cuando el envío SMTP falla")
    void sendWelcomeEmail_SendFails_PropagatesException() {
        when(mailSender.createMimeMessage()).thenReturn(realMimeMessage());
        doThrow(new MailSendException("SMTP caído")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> emailService.sendWelcomeEmail(player()))
                .isInstanceOf(MailSendException.class);
    }
}
