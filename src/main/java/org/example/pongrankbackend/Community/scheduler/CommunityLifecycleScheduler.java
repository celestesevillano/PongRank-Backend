package org.example.pongrankbackend.Community.scheduler;

import org.example.pongrankbackend.Community.repository.CommunityRepository;
import org.example.pongrankbackend.Community.service.CommunityLifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CommunityLifecycleScheduler {
    private static final Logger log = LoggerFactory.getLogger(CommunityLifecycleScheduler.class);
    private final CommunityRepository communities;
    private final CommunityLifecycle lifecycle;

    public CommunityLifecycleScheduler(CommunityRepository communities, CommunityLifecycle lifecycle) {
        this.communities = communities;
        this.lifecycle = lifecycle;
    }

    @Scheduled(fixedDelayString = "${community.lifecycle.refresh-ms:60000}")
    public void reconcile() {
        for (Long id : communities.findLifecycleCommunityIds()) {
            try {
                lifecycle.reconcile(id);
            } catch (RuntimeException failure) {
                log.error("No se pudo actualizar el estado de la comunidad {}", id, failure);
            }
        }
    }
}
