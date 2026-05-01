package com.das.cleanddd.domain.healthcareprof.ports;

import com.das.cleanddd.domain.healthcareprof.events.HcpDomainEvent;

public interface IHcpEventPublisher {
    void publish(HcpDomainEvent event);
}
