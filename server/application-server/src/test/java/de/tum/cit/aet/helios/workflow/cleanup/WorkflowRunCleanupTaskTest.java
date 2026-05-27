package de.tum.cit.aet.helios.workflow.cleanup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.branch.github.GitHubBranchSyncService;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
import de.tum.cit.aet.helios.workflow.WorkflowRunRepository.OrphanBranchRunCandidate;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHRepository;

class WorkflowRunCleanupTaskTest {

  private static final int GRACE = 7;
  private static final int BATCH = 5000;
  private static final long REPO_ID = 1L;
  private static final String REPO = "ls1intum/Helios";

  @Test
  void deletesRunsWhoseBranchGitHubConfirmsAreGone() throws Exception {
    Fixture f = new Fixture();
    f.liveBranches("main"); // GitHub: only 'main' still exists
    f.candidatesForRepo(candidate(100L, "feature-gone"));

    f.task().purgeOrphanBranchRuns();

    verify(f.repo).deleteAllByIdInBatch(List.of(100L));
    verify(f.branchSync, never()).processBranch(any());
  }

  @Test
  void healsBranchStillOnGitHubInsteadOfDeleting() throws Exception {
    Fixture f = new Fixture();
    f.liveBranches("main", "feature-live"); // branch still exists on GitHub
    GHBranch ghBranch = mock(GHBranch.class);
    when(f.ghRepo.getBranch("feature-live")).thenReturn(ghBranch);
    f.candidatesForRepo(candidate(100L, "feature-live"));

    f.task().purgeOrphanBranchRuns();

    verify(f.branchSync).processBranch(ghBranch); // re-synced
    verify(f.repo, never()).deleteAllByIdInBatch(any()); // not deleted
  }

  @Test
  void deletesGoneBranchButHealsLiveOneInSameBatch() throws Exception {
    Fixture f = new Fixture();
    f.liveBranches("main", "feature-live");
    GHBranch ghBranch = mock(GHBranch.class);
    when(f.ghRepo.getBranch("feature-live")).thenReturn(ghBranch);
    f.candidatesForRepo(candidate(100L, "feature-gone"), candidate(101L, "feature-live"));

    f.task().purgeOrphanBranchRuns();

    verify(f.repo).deleteAllByIdInBatch(List.of(100L)); // gone branch swept
    verify(f.branchSync).processBranch(ghBranch); // live branch healed, 101 kept
  }

  @Test
  void skipsRepoWhenGitHubCannotBeReached() throws Exception {
    Fixture f = new Fixture();
    when(f.repo.findRepositoriesWithOrphanCandidates(GRACE)).thenReturn(List.of(REPO_ID));
    GitRepository gitRepo = mock(GitRepository.class);
    when(gitRepo.getNameWithOwner()).thenReturn(REPO);
    when(f.gitRepoRepo.findByRepositoryId(REPO_ID)).thenReturn(Optional.of(gitRepo));
    when(f.gitHub.getRepository(REPO)).thenThrow(new IOException("github down"));

    f.task().purgeOrphanBranchRuns();

    // Fail-safe: never deletes or heals, never even queries candidates for the repo.
    verify(f.repo, never()).deleteAllByIdInBatch(any());
    verify(f.branchSync, never()).processBranch(any());
    verify(f.repo, never()).findOrphanBranchRunCandidatesForRepo(anyLong(), anyInt(), anyInt());
  }

  @Test
  void dryRunOnlyCountsNoDeleteNoHeal() {
    Fixture f = new Fixture();
    f.dryRun = true;
    when(f.repo.countOrphanBranchRunIds(GRACE)).thenReturn(42L);

    f.task().purgeOrphanBranchRuns();

    verify(f.repo).countOrphanBranchRunIds(GRACE);
    verify(f.repo, never()).deleteAllByIdInBatch(any());
    verify(f.branchSync, never()).processBranch(any());
    verifyNoInteractions(f.gitHub);
  }

  @Test
  void shortCircuitsWhenDisabled() {
    Fixture f = new Fixture();
    f.enabled = false;

    f.task().purgeOrphanBranchRuns();

    verifyNoInteractions(f.repo, f.gitHub, f.branchSync, f.gitRepoRepo);
  }

  @Test
  void skipsInvalidBatchSize() {
    Fixture f = new Fixture();
    f.batchSize = 0; // invalid: before the guard this could loop forever

    f.task().purgeOrphanBranchRuns();

    verifyNoInteractions(f.repo, f.gitHub, f.branchSync, f.gitRepoRepo);
  }

  @Test
  void orphanBranchesDefaultsAreSafe() {
    // Defaults must keep the sweep off until an operator explicitly enables it.
    WorkflowRunCleanupProps.OrphanBranches defaults = new WorkflowRunCleanupProps.OrphanBranches();
    assertFalse(defaults.isEnabled());
    assertEquals(7, defaults.getGraceDays());
    assertEquals(5000, defaults.getBatchSize());
  }

  private static OrphanBranchRunCandidate candidate(long id, String headBranch) {
    return new OrphanBranchRunCandidate() {
      @Override
      public Long getId() {
        return id;
      }

      @Override
      public String getHeadBranch() {
        return headBranch;
      }
    };
  }

  /** Wires the task's collaborators as bare mocks; tests stub only what they need. */
  private static final class Fixture {
    final WorkflowRunRepository repo = mock(WorkflowRunRepository.class);
    final GitHubService gitHub = mock(GitHubService.class);
    final GitHubBranchSyncService branchSync = mock(GitHubBranchSyncService.class);
    final GitRepoRepository gitRepoRepo = mock(GitRepoRepository.class);
    final GHRepository ghRepo = mock(GHRepository.class);

    boolean enabled = true;
    boolean dryRun = false;
    int graceDays = GRACE;
    int batchSize = BATCH;

    WorkflowRunCleanupTask task() {
      WorkflowRunCleanupProps props = new WorkflowRunCleanupProps();
      props.setDryRun(dryRun);
      WorkflowRunCleanupProps.OrphanBranches orphan = new WorkflowRunCleanupProps.OrphanBranches();
      orphan.setEnabled(enabled);
      orphan.setGraceDays(graceDays);
      orphan.setBatchSize(batchSize);
      props.setOrphanBranches(orphan);
      return new WorkflowRunCleanupTask(repo, props, gitHub, branchSync, gitRepoRepo);
    }

    /** Repo has candidates, resolves to {@link #REPO}; GitHub returns the given branches. */
    void liveBranches(String... names) throws IOException {
      when(repo.findRepositoriesWithOrphanCandidates(graceDays)).thenReturn(List.of(REPO_ID));
      GitRepository gitRepo = mock(GitRepository.class);
      when(gitRepo.getNameWithOwner()).thenReturn(REPO);
      when(gitRepoRepo.findByRepositoryId(REPO_ID)).thenReturn(Optional.of(gitRepo));
      when(gitHub.getRepository(REPO)).thenReturn(ghRepo);
      Map<String, GHBranch> branches = new HashMap<>();
      for (String name : names) {
        branches.put(name, mock(GHBranch.class));
      }
      when(ghRepo.getBranches()).thenReturn(branches);
    }

    /** First candidate query returns the given candidates, then drains to empty. */
    void candidatesForRepo(OrphanBranchRunCandidate... candidates) {
      when(repo.findOrphanBranchRunCandidatesForRepo(REPO_ID, graceDays, batchSize))
          .thenReturn(List.of(candidates))
          .thenReturn(List.of());
    }
  }
}
