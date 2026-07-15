package de.tum.cit.aet.helios.workflow.cleanup;

import de.tum.cit.aet.helios.branch.github.GitHubBranchSyncService;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * This class is responsible for cleaning up obsolete workflow runs based on configured policies.
 * It is scheduled to run every day at 1 AM.
 *
 * <p>It uses the {@link WorkflowRunRepository} to perform the actual deletion of obsolete runs.
 * The policies are defined in the {@link WorkflowRunCleanupProps} class.
 */
@RequiredArgsConstructor
@Component
@Log4j2
public class WorkflowRunCleanupTask {

  private final WorkflowRunRepository repo;
  private final WorkflowRunCleanupProps props;
  private final GitHubService gitHubService;
  private final GitHubBranchSyncService branchSyncService;
  private final GitRepoRepository gitRepoRepository;

  /**
   * This method is called when the application is ready. It logs the current mode of operation
   * (DRY-RUN or DELETE) based on the configuration.
   */
  @EventListener(ApplicationReadyEvent.class)
  public void init() {
    if (props.isDryRun()) {
      log.info("Workflow-run cleanup is in DRY-RUN mode. No rows will be deleted.");
    } else {
      log.info("Workflow-run cleanup is in DELETE mode.");
    }
  }


  /**
   * This method is scheduled to run every day at 1 AM. It purges workflow runs based on the
   * configured policies.
   */
  @Scheduled(cron = "${cleanup.workflow-run.cron:0 0 1 * * *}")
  @Transactional
  public void purge() {
    log.info("Workflow-run cleanup started.");
    int totalDeleted = 0;

    for (WorkflowRunCleanupProps.Policy policy : props.getPolicies()) {

      String tps = policy.getTestProcessingStatus();
      if (tps != null && tps.trim().isEmpty()) {
        tps = null;
      }

      int deleted = 0;

      if (props.isDryRun()) {
        List<Long> ids = repo.previewObsoleteRunIds(
            policy.getKeep(), policy.getAgeDays(), tps);

        deleted = ids.size();

        log.info(
            "DRY-RUN: Cleanup policy tps={} keep={} ageDays={}  →  {} rows will be deleted",
            tps, policy.getKeep(), policy.getAgeDays(), ids.size());

        List<Long> idsOfSurvivingRuns = repo.previewSurvivorRunIds(
            policy.getKeep(), policy.getAgeDays(), tps);

        log.info(
            "DRY-RUN: Cleanup policy tps={} keep={} ageDays={}  →  {} rows will survive: {}",
            tps, policy.getKeep(), policy.getAgeDays(), idsOfSurvivingRuns.size(),
            idsOfSurvivingRuns);

      } else {
        deleted = repo.purgeObsoleteRuns(
            policy.getKeep(),
            policy.getAgeDays(),
            tps
        );

        log.info("DELETE: Cleanup policy  tps={}  keep={}  ageDays={}  →  {} rows deleted",
            tps, policy.getKeep(),
            policy.getAgeDays(), deleted);
      }

      totalDeleted += deleted;
    }

    totalDeleted += applyMaxAgeCap();

    log.info("Workflow-run cleanup finished.  Total rows deleted: {}", totalDeleted);
  }

  /**
   * Applies the hard retention cap ({@code cleanup.workflow-run.max-age-days}):
   * deletes every run older than the configured age, regardless of status,
   * branch (default branches included) or keep-N policy. Runs after the
   * policies so their logs reflect what the keep-N rules alone would do.
   *
   * @return number of rows deleted (0 when disabled or in dry-run mode)
   */
  private int applyMaxAgeCap() {
    Integer maxAgeDays = props.getMaxAgeDays();
    if (maxAgeDays == null) {
      return 0;
    }
    if (maxAgeDays < 1) {
      log.warn("Max-age cap skipped: invalid configuration (maxAgeDays={}). Require >= 1.",
          maxAgeDays);
      return 0;
    }

    if (props.isDryRun()) {
      long count = repo.countRunsOlderThan(maxAgeDays);
      log.info("DRY-RUN: Max-age cap maxAgeDays={}  →  {} rows will be deleted",
          maxAgeDays, count);
      return 0;
    }

    int deleted = repo.purgeRunsOlderThan(maxAgeDays);
    log.info("DELETE: Max-age cap  maxAgeDays={}  →  {} rows deleted", maxAgeDays, deleted);
    return deleted;
  }


  /**
   * Sweeps workflow runs whose {@code head_branch} no longer exists — typically
   * feature branches deleted after a PR merged. The keep-N policy in
   * {@link #purge()} cannot detect these, so they would otherwise accumulate.
   *
   * <p>Runs daily at 03:30, well clear of the keep-N sweep at 01:00. Honours the
   * same {@link WorkflowRunCleanupProps#isDryRun()} flag. Runs younger than
   * {@code graceDays}, with a {@code NULL head_branch} (tag/scheduled/dispatch),
   * with a {@code NULL repository_id}, or still referenced by a
   * {@code helios_deployment}/{@code deployment} row are never swept.
   *
   * <p><b>GitHub confirmation:</b> the DB only yields <em>candidates</em>
   * (branches absent from our local {@code branch} table). Before deleting, each
   * candidate's branch is confirmed against GitHub's live branch list for that
   * repo (one cheap listing per repo). If the branch still exists on GitHub, the
   * run is <em>not</em> deleted — instead the branch is re-synced (a targeted
   * single-branch sync) to heal the local table, since its absence was a sync
   * gap rather than a real deletion. This guarantees a run is only deleted once
   * GitHub itself confirms its branch is gone. If GitHub can't be reached for a
   * repo, that repo is skipped for the run (fail-safe: nothing deleted).
   *
   * <p>Confirmed orphans are deleted by id in batches of
   * {@link WorkflowRunCleanupProps.OrphanBranches#getBatchSize()} (cascading to
   * {@code test_suite}/{@code test_case}/junction rows via FK constraints); each
   * batch is its own short transaction and the outer loop is non-transactional.
   */
  @Scheduled(cron = "${cleanup.workflow-run.orphan-branches.cron:0 30 3 * * *}")
  public void purgeOrphanBranchRuns() {
    WorkflowRunCleanupProps.OrphanBranches cfg = props.getOrphanBranches();
    if (!cfg.isEnabled()) {
      log.debug("Orphan-branch cleanup skipped (disabled).");
      return;
    }

    int graceDays = cfg.getGraceDays();
    int batchSize = cfg.getBatchSize();
    if (graceDays < 0 || batchSize < 1) {
      log.warn(
          "Orphan-branch cleanup skipped: invalid configuration (graceDays={}, batchSize={}). "
              + "Require graceDays >= 0 and batchSize >= 1.",
          graceDays, batchSize);
      return;
    }

    if (props.isDryRun()) {
      long total = repo.countOrphanBranchRunIds(graceDays);
      log.info(
          "DRY-RUN: Orphan-branch cleanup graceDays={}  →  {} candidate rows (pre-GitHub-"
              + "confirmation; actual deletions exclude branches still present on GitHub).",
          graceDays, total);
      return;
    }

    log.info("Orphan-branch cleanup started (graceDays={}, batchSize={}).", graceDays, batchSize);

    int totalDeleted = 0;
    int totalHealed = 0;
    for (Long repositoryId : repo.findRepositoriesWithOrphanCandidates(graceDays)) {
      Set<String> liveBranches = fetchLiveBranches(repositoryId);
      if (liveBranches == null) {
        // Couldn't confirm this repo against GitHub — skip it this run (fail-safe).
        continue;
      }
      int[] counts = sweepRepository(repositoryId, graceDays, batchSize, liveBranches);
      totalDeleted += counts[0];
      totalHealed += counts[1];
    }

    log.info(
        "Orphan-branch cleanup finished.  Rows deleted: {}, branches re-synced (sync gaps): {}.",
        totalDeleted, totalHealed);
  }

  /**
   * Sweeps a single repository's orphan candidates, confirming each against the
   * already-fetched {@code liveBranches} set.
   *
   * @return {@code [rowsDeleted, branchesHealed]}
   */
  private int[] sweepRepository(
      long repositoryId, int graceDays, int batchSize, Set<String> liveBranches) {
    int deleted = 0;
    int healed = 0;
    // Heal each gap branch at most once per run (guards against re-processing).
    Set<String> healedBranches = new HashSet<>();

    while (true) {
      List<WorkflowRunRepository.OrphanBranchRunCandidate> candidates =
          repo.findOrphanBranchRunCandidatesForRepo(repositoryId, graceDays, batchSize);
      if (candidates.isEmpty()) {
        break;
      }

      List<Long> confirmedOrphanIds = new ArrayList<>();
      boolean healedThisBatch = false;
      for (WorkflowRunRepository.OrphanBranchRunCandidate candidate : candidates) {
        if (liveBranches.contains(candidate.getHeadBranch())) {
          // The branch still exists on GitHub but is missing from our table:
          // a sync gap. Re-sync it instead of deleting the run.
          if (healedBranches.add(candidate.getHeadBranch())
              && healBranch(repositoryId, candidate.getHeadBranch())) {
            healed++;
            healedThisBatch = true;
          }
        } else {
          confirmedOrphanIds.add(candidate.getId());
        }
      }

      if (!confirmedOrphanIds.isEmpty()) {
        repo.deleteAllByIdInBatch(confirmedOrphanIds);
        deleted += confirmedOrphanIds.size();
      }

      // Stop when a batch produced neither a deletion nor a fresh heal: the
      // remaining candidates are gap branches already handled (or heals that
      // failed), so another pass cannot make progress.
      if (confirmedOrphanIds.isEmpty() && !healedThisBatch) {
        break;
      }
    }
    return new int[] {deleted, healed};
  }

  /**
   * Fetches the live branch names for a repository from GitHub (one cheap
   * listing, no per-branch calls).
   *
   * @return the set of live branch names, or {@code null} if the repository
   *     can't be resolved or GitHub can't be reached (caller skips the repo).
   */
  private Set<String> fetchLiveBranches(long repositoryId) {
    Optional<GitRepository> repository = gitRepoRepository.findByRepositoryId(repositoryId);
    if (repository.isEmpty()) {
      log.warn("Orphan-branch cleanup: no repository row for repositoryId={}; skipping.",
          repositoryId);
      return null;
    }
    String nameWithOwner = repository.get().getNameWithOwner();
    try {
      GHRepository ghRepository = gitHubService.getRepository(nameWithOwner);
      Set<String> names = new HashSet<>(ghRepository.getBranches().keySet());
      log.info("Orphan-branch cleanup: {} live branches fetched for {}.", names.size(),
          nameWithOwner);
      return names;
    } catch (IOException | RuntimeException e) {
      log.warn("Orphan-branch cleanup: failed to fetch live branches for {}; skipping repo this "
          + "run (nothing deleted). Cause: {}", nameWithOwner, e.toString());
      return null;
    }
  }

  /**
   * Targeted single-branch re-sync to heal a branch that exists on GitHub but
   * went missing from the local {@code branch} table.
   *
   * @return {@code true} if the branch was re-synced; {@code false} on failure
   *     (the run is then left in place rather than deleted).
   */
  private boolean healBranch(long repositoryId, String branchName) {
    Optional<GitRepository> repository = gitRepoRepository.findByRepositoryId(repositoryId);
    if (repository.isEmpty()) {
      return false;
    }
    String nameWithOwner = repository.get().getNameWithOwner();
    try {
      GHRepository ghRepository = gitHubService.getRepository(nameWithOwner);
      GHBranch ghBranch = ghRepository.getBranch(branchName);
      branchSyncService.processBranch(ghBranch);
      log.info("Orphan-branch cleanup: branch '{}' still exists on {} but was missing locally — "
          + "re-synced it instead of deleting its runs.", branchName, nameWithOwner);
      return true;
    } catch (IOException | RuntimeException e) {
      log.warn("Orphan-branch cleanup: failed to re-sync branch '{}' on {}; leaving its runs in "
          + "place. Cause: {}", branchName, nameWithOwner, e.toString());
      return false;
    }
  }
}
