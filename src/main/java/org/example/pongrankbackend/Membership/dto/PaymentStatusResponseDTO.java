package org.example.pongrankbackend.Membership.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.pongrankbackend.Membership.MembershipStatus;
import org.example.pongrankbackend.Membership.PaymentStatus;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentStatusResponseDTO {

    private Long transactionId;
    private PaymentStatus status;
    private BigDecimal amount;
    private MembershipStatus membershipStatus;
}
