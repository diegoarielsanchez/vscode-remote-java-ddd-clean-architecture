package com.das.cleanddd.domain.assistant.ports;

import java.util.List;

/** Reads from healthcare-prof-service, made as the calling rep. */
public interface IHcpPort {

    List<HcpView> findHcps(String query, int page);
}
