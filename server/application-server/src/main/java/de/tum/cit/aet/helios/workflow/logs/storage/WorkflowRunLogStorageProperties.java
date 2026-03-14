package de.tum.cit.aet.helios.workflow.logs.storage;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "helios.logs")
public record WorkflowRunLogStorageProperties(Path basePath) {}
