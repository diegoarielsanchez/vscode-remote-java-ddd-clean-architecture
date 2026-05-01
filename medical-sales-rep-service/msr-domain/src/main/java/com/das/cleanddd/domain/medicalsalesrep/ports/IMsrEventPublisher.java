package com.das.cleanddd.domain.medicalsalesrep.ports;

import com.das.cleanddd.domain.medicalsalesrep.events.MsrDomainEvent;

public interface IMsrEventPublisher {

    void publish(MsrDomainEvent event);
}
