package de.tum.cit.aet.helios.workflow.pipeline;

import de.tum.cit.aet.helios.workflow.pipeline.config.PipelineConfigDto.NodeConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.Nullable;

/**
 * Global pipeline defaults that are not (yet) per-repository. Per-repo categories and nodes live in
 * the database (see {@code PipelineConfigService}); this holds only the optional merge-readiness
 * {@code gate} node — mapped to the CI's single required-checks job and rendered as a header badge.
 * A per-repository gate is a planned follow-up.
 */
@ConfigurationProperties(prefix = "helios.pipeline")
public record PipelineProperties(@Nullable NodeConfig gate) {}
