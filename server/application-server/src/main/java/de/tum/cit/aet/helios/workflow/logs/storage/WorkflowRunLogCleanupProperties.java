package de.tum.cit.aet.helios.workflow.logs.storage;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "helios.logs.cleanup")
public record WorkflowRunLogCleanupProperties(
    @DefaultValue("0 0 2 * * *") String cron,
    @DefaultValue("1d") Duration maxAge,
    @DefaultValue("false") boolean dryRun) {}
