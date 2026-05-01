package com.das.inframySQL.service.visit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

/**
 * Option B — consumes MSR domain events and keeps the local
 * {@link MsrSnapshotEntity} table up-to-date.
 */
@Service
public class MsrSnapshotUpdater {

    private static final Logger log = LoggerFactory.getLogger(MsrSnapshotUpdater.class);

    private final MsrSnapshotJpaRepository jpaRepo;

    public MsrSnapshotUpdater(MsrSnapshotJpaRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @RabbitListener(queues = "visit-service.msr.queue",
                    containerFactory = "visitRabbitListenerContainerFactory")
    public void onMsrEvent(MsrEventMessage msg) {
        if (msg == null || msg.id() == null) {
            log.warn("Received null or id-less MSR event message, ignoring");
            return;
        }
        log.debug("Received MSR event: type={} id={}", msg.eventType(), msg.id());
        switch (msg.eventType()) {
            case "MSR_CREATED", "MSR_UPDATED" -> upsert(msg);
            case "MSR_ACTIVATED"   -> updateActive(msg.id(), Boolean.TRUE);
            case "MSR_DEACTIVATED" -> updateActive(msg.id(), Boolean.FALSE);
            default -> log.warn("Unknown MSR event type: {}", msg.eventType());
        }
    }

    private void upsert(MsrEventMessage msg) {
        MsrSnapshotEntity entity = jpaRepo.findById(msg.id())
                .orElseGet(MsrSnapshotEntity::new);
        entity.setId(msg.id());
        entity.setName(msg.name());
        entity.setSurname(msg.surname());
        entity.setEmail(msg.email());
        entity.setActive(msg.active());
        jpaRepo.save(entity);
        log.info("MSR snapshot upserted: id={}", msg.id());
    }

    private void updateActive(String id, boolean active) {
        jpaRepo.findById(id).ifPresentOrElse(entity -> {
            entity.setActive(active);
            jpaRepo.save(entity);
            log.info("MSR snapshot active updated: id={} active={}", id, active);
        }, () -> log.warn("MSR snapshot not found for active update: id={}", id));
    }
}
