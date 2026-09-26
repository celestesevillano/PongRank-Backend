package org.example.pongrankbackend.email.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.example.pongrankbackend.Membership.MembershipPlan;
import org.example.pongrankbackend.Player.Player;
import org.example.pongrankbackend.common.exception.EmailDeliveryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${mail.from}")
    private String fromAddress;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.backend-url}")
    private String backendUrl;

    public EmailServiceImpl(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    // Todas las plantillas llevan el logo de PongRank en el header; los clientes de correo no
    // pueden cargar rutas relativas ni archivos locales, así que necesitan la URL pública absoluta
    private Context newContext() {
        Context context = new Context();
        context.setVariable("logoUrl", backendUrl + "/images/pongrank-logo.jpg");
        return context;
    }

    @Override
    @Async("taskExecutor")
    public void sendWelcomeEmail(Player player) {
        Context context = newContext();
        context.setVariable("playerName", player.getName());
        context.setVariable("loginLink", frontendUrl + "/login");

        send(player.getEmail(), "¡Bienvenido a PongRank!", "email/welcome-email", context);
    }

    @Override
    @Async("taskExecutor")
    public void sendPasswordResetEmail(Player player, String resetLink, int expirationMinutes) {
        Context context = newContext();
        context.setVariable("playerName", player.getName());
        context.setVariable("resetLink", resetLink);
        context.setVariable("expirationMinutes", expirationMinutes);

        send(player.getEmail(), "Restablece tu contraseña — PongRank", "email/password-reset-email", context);
    }

    @Override
    @Async("taskExecutor")
    public void sendPaymentConfirmationEmail(Player player, MembershipPlan plan, BigDecimal amount, String transactionId) {
        Context context = newContext();
        context.setVariable("playerName", player.getName());
        context.setVariable("planName", plan.name());
        context.setVariable("amount", "S/ " + amount.toPlainString());
        context.setVariable("paymentDate", LocalDateTime.now().format(DATE_FORMAT));
        context.setVariable("transactionId", transactionId);
        context.setVariable("accountLink", frontendUrl + "/account/membership");

        send(player.getEmail(), "Pago confirmado — PongRank", "email/payment-confirmation-email", context);
    }

    @Override
    @Async("taskExecutor")
    public void sendAccountDeletedEmail(Player player, MembershipPlan plan) {
        Context context = newContext();
        context.setVariable("playerName", player.getName());
        context.setVariable("planName", plan.name());

        send(player.getEmail(), "Tu cuenta ha sido eliminada — PongRank", "email/account-deleted-email", context);
    }

    private void send(String to, String subject, String template, Context context) {
        try {
            String html = templateEngine.process(template, context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(message);
        } catch (MessagingException e) {
            log.error("No se pudo enviar el correo '{}' a {}", subject, to, e);
            throw new EmailDeliveryException("No se pudo enviar el correo a " + to);
        }
    }
}
