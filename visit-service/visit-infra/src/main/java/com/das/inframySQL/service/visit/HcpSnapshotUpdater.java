package com.das.inframySQL.service.visit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

/**
 * Option B — consumes HCP domain events and keeps the local
 * {@link HcpSnapshotEntity} table up-to-date.
 */
@Service
public class HcpSnapshotUpdater {

    private static final Logger log = LoggerFactory.getLogger(HcpSnapshotUpdater.class);

    private final HcpSnapshotJpaRepository jpaRepo;

    public HcpSnapshotUpdater(HcpSnapshotJpaRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @RabbitListener(queues = "visit-service.hcp.queue",
                    containerFactory = "visitRabbitListenerContainerFactory")
    public void onHcpEvent(HcpEventMessage msg) {
        if (msg == null || msg.id() == null) {
            log.warn("Received null or id-less HCP event message, ignoring");
            return;
        }
        log.debug("Received HCP event: type={} id={}", msg.eventType(), msg.id());
        switch (msg.eventType()) {
            case "HCP_CREATED", "HCP_UPDATED" -> upsert(msg);
            case "HCP_ACTIVATED"   -> updateActive(msg.id(), Boolean.TRUE);
            case "HCP_DEACTIVATED" -> updateActive(msg.id(), Boolean.FALSE);
            default -> log.warn("Unknown HCP event type: {}", msg.eventType());
        }
    }

    private void upsert(HcpEventMessage msg) {
        HcpSnapshotEntity entity = jpaRepo.findById(msg.id())
                .orElseGet(HcpSnapshotEntity::new);
        entity.setId(msg.id());
        entity.setName(msg.name());
        entity.setSurname(msg.surname());
        entity.setEmail(msg.email());
        entity.setActive(msg.active());
        jpaRepo.save(entity);
        log.info("HCP snapshot upserted: id={}", msg.id());
    }

    private void updateActive(String id, boolean active) {
        jpaRepo.findById(id).ifPresentOrElse(entity -> {
            entity.setActive(active);
            jpaRepo.save(entity);
            log.info("HCP snapshot active updated: id={} active={}", id, active);
        }, () -> log.warn("HCP snapshot not found for active update: id={}", id));
    }
}
