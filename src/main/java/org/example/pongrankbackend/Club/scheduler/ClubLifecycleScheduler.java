package org.example.pongrankbackend.Club.scheduler;

import org.example.pongrankbackend.Club.repository.ClubRepository;
import org.example.pongrankbackend.Club.service.ClubLifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ClubLifecycleScheduler {
    private static final Logger log = LoggerFactory.getLogger(ClubLifecycleScheduler.class);
    private final ClubRepository clubs;
    private final ClubLifecycle lifecycle;

    public ClubLifecycleScheduler(ClubRepository clubs, ClubLifecycle lifecycle) {
        this.clubs = clubs;
        this.lifecycle = lifecycle;
    }

    @Scheduled(fixedDelayString = "${club.lifecycle.refresh-ms:60000}")
    public void reconcile() {
        for (Long id : clubs.findLifecycleIds(ClubLifecycle.TRACKED)) {
            try {
                lifecycle.reconcile(id);
            } catch (RuntimeException failure) {
                log.error("No se pudo actualizar el estado del club {}", id, failure);
            }
        }
    }
}
