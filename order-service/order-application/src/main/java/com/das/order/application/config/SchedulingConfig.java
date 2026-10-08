package com.das.order.application.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Runs the outbox relay and cleanup jobs (not in dev, where events are discarded). */
@Configuration
@Profile("!dev")
@EnableScheduling
public class SchedulingConfig {
}
