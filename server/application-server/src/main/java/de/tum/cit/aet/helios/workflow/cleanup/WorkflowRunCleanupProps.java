package de.tum.cit.aet.helios.workflow.cleanup;

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised configuration for the nightly workflow‑run clean‑up.
 *
 * <p>It binds the YAML fragment
 * <pre>{@code
 * cleanup:
 *   workflow-run:
 *     policies:
 *       - test-processing-status: PROCESSED
 *         keep: 2
 *         age-days: 10
 *       - test-processing-status: FAILED
 *         keep: 1
 *         age-days: 30
 * }</pre>
 *
 * <p>into a list of {@link Policy} records.  You may add, remove or
 * reorder policies without touching any Java code; simply change the
 * {@code application.yml} and restart.
 */
@Data
@ConfigurationProperties(prefix = "cleanup.workflow-run")
public class WorkflowRunCleanupProps {

  /**
   * Cron expression that controls when the clean-up job runs.
   * Default   → "0 0 1 * * *"  (every day at 01:00)
   */
  private String cron = "0 0 1 * * *";

  /**
   * If {@code true}, the clean-up job will only log what it would
   * have done, but not actually delete anything.  This is useful for
   * testing and debugging.
   *
   * <p>Default: {@code true}</p>
   */
  private boolean dryRun = true;

  /**
   * Ordered list of retention policies.  The task iterates over them
   * in the declared order and applies each individually.
   *
   * <p>Empty list ⇒ no clean‑up at all.</p>
   */
  private List<Policy> policies = List.of();

  /**
   * Settings for the orphan-branch sweep — deletes workflow runs whose
   * {@code head_branch} no longer exists in the {@code branch} table,
   * with a grace period to avoid races with branch-sync state.
   */
  private OrphanBranches orphanBranches = new OrphanBranches();

  /**
   * A single retention rule that targets one {@code test_processing_status}.
   */
  @Data
  public static class Policy {

    /**
     * Which {@code test_processing_status} should be cleaned by this
     * policy.  Use {@code null} (or omit the YAML key) to match every
     * status not handled by a previous policy.
     */
    private String testProcessingStatus;

    /**
     * Keep this many <em>latest</em> workflow runs per ⟨repository,branch⟩
     * that have the selected status.  Example: a value of 2 keeps the
     * newest two processed runs for every repo + branch combination.
     */
    private int keep;

    /**
     * How old (in days) a run must be <em>in addition</em> to being
     * outside the “keep N” window before it is deleted.  Use 0 to
     * ignore age completely.
     */
    private int ageDays;
  }

  /**
   * Configuration for the orphan-branch sweep. A workflow run is considered
   * orphaned when its {@code head_branch} no longer matches any row in the
   * {@code branch} table for the same repository. The sweep also excludes
   * runs still referenced by {@code helios_deployment} or {@code deployment}
   * so the deployment → build link is preserved.
   */
  @Data
  public static class OrphanBranches {

    /**
     * Master switch for the orphan-branch sweep. Defaults to {@code true};
     * the parent {@link WorkflowRunCleanupProps#dryRun} flag still gates
     * whether anything is actually deleted.
     */
    private boolean enabled = true;

    /**
     * Minimum age (in days) before an orphan run becomes eligible for
     * deletion. Acts as a grace window for transient branch-sync state.
     */
    private int graceDays = 7;

    /**
     * Cron expression for the orphan sweep. Declared here so the value is
     * discoverable in IDE config completion; the actual schedule lives in
     * the {@code @Scheduled} annotation on {@code WorkflowRunCleanupTask}.
     */
    private String cron = "0 30 1 * * *";
  }
}
