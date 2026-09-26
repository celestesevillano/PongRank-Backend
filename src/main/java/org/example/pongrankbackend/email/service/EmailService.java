package org.example.pongrankbackend.email.service;

import org.example.pongrankbackend.Membership.MembershipPlan;
import org.example.pongrankbackend.Player.Player;

import java.math.BigDecimal;

public interface EmailService {

    void sendWelcomeEmail(Player player);

    void sendPasswordResetEmail(Player player, String resetLink, int expirationMinutes);

    void sendPaymentConfirmationEmail(Player player, MembershipPlan plan, BigDecimal amount, String transactionId);
}
