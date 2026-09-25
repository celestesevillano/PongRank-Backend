package org.example.pongrankbackend.Membership.scheduler;

import org.example.pongrankbackend.Membership.service.MembershipService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// activatePaidMembership fija el endDate pero nada lo revisa después; sin esto una membresía
// vencida se queda en ACTIVE para siempre en la base. Corre una vez al día, de madrugada.
@Component
public class MembershipExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(MembershipExpirationScheduler.class);

    private final MembershipService membershipService;

    public MembershipExpirationScheduler(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @Scheduled(cron = "${membership.expiration.cron:0 0 3 * * *}")
    public void expireOverdueMemberships() {
        try {
            membershipService.expireOverdueMemberships();
        } catch (Exception e) {
            log.error("No se pudo correr el job de vencimiento de membresías", e);
        }
    }
}
