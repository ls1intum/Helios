package de.tum.cit.aet.helios.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's scheduling in every profile except {@code openapi}. The {@code openapi} profile
 * boots the full application against an empty in-memory H2 purely to dump the OpenAPI spec; running
 * {@code @Scheduled} reconcilers there fails (their tables don't exist) and, worse, can call the
 * live GitHub API. Gating {@code @EnableScheduling} here keeps spec generation side-effect free
 * while leaving scheduling on for dev/staging/prod. (Previously {@code @EnableScheduling} lived on
 * {@link AsyncConfig} and ran unconditionally.)
 */
@Configuration
@Profile("!openapi")
@EnableScheduling
public class SchedulingConfig {}
