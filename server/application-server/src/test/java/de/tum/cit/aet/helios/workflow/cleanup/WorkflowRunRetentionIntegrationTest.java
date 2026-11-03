package de.tum.cit.aet.helios.workflow.cleanup;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration test for the max-age-cap native queries in {@link WorkflowRunRepository}.
 *
 * <p>Runs against an embedded PostgreSQL (zonky) with the real Flyway schema, exercising the SQL
 * semantics a mocked-repository unit test cannot: the hard age cap (default branches included) with
 * its deployment exclusion and batch bound.
 */
@DataJpaTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureEmbeddedDatabase(
    type = AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES,
    provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.DOCKER)
@Import(WorkflowRunRetentionIntegrationTest.CacheTestConfig.class)
class WorkflowRunRetentionIntegrationTest {

  /**
   * The production entities/repositories carry {@code @Cacheable} metadata, but the
   * {@code @DataJpaTest} slice does not load the cache auto-configuration. Provide a simple
   * in-memory {@link CacheManager} so the caching aspect can initialize.
   */
  @TestConfiguration
  static class CacheTestConfig {
    @Bean
    CacheManager cacheManager() {
      return new ConcurrentMapCacheManager();
    }
  }

  private static final String DEFAULT_BRANCH = "develop";
  private static final String FEATURE_BRANCH = "feature-x";

  // Repo 1 (default branch 'develop').
  // develop: three runs; only the oldest exceeds the max-age cap.
  private static final long DEVELOP_NEWEST = 100L;
  private static final long DEVELOP_MIDDLE = 101L;
  private static final long DEVELOP_OLD = 102L; // 120 days — only the max-age cap removes it
  // feature branch: three runs, none older than the max-age cap.
  private static final long FEATURE_NEWEST = 110L;
  private static final long FEATURE_MIDDLE = 111L;
  private static final long FEATURE_OLD = 112L;
  // 120 days old but referenced by a deployment — the max-age cap must preserve it.
  private static final long DEPLOYED_OLD = 120L;
  // 120 days old but referenced by a helios_deployment — the max-age cap must preserve it too.
  private static final long HELIOS_DEPLOYED_OLD = 121L;
  // Repo 2 (default_branch NULL): NULL-head_branch runs are only removed by the max-age cap.
  private static final long NULL_BRANCH_NEWEST = 130L;
  private static final long NULL_BRANCH_MIDDLE = 131L;
  private static final long NULL_BRANCH_OLD = 132L;

  @Autowired private WorkflowRunRepository repo;
  @Autowired private DataSource dataSource;
  private JdbcTemplate jdbc;

  @BeforeEach
  void seed() {
    jdbc = new JdbcTemplate(dataSource);

    jdbc.update(
        "INSERT INTO repository (repository_id, has_issues, has_projects, has_wiki, is_archived, "
            + "is_disabled, is_private, stargazers_count, watchers_count, name_with_owner, "
            + "default_branch) "
            + "VALUES (1, false, false, false, false, false, false, 0, 0, 'ls1intum/Helios', ?)",
        DEFAULT_BRANCH);
    jdbc.update(
        "INSERT INTO repository (repository_id, has_issues, has_projects, has_wiki, is_archived, "
            + "is_disabled, is_private, stargazers_count, watchers_count, name_with_owner, "
            + "default_branch) "
            + "VALUES (2, false, false, false, false, false, false, 0, 0, 'ls1intum/Other', NULL)");
    jdbc.update("INSERT INTO workflow (id, repository_id, name) VALUES (1, 1, 'CI')");
    jdbc.update("INSERT INTO workflow (id, repository_id, name) VALUES (2, 2, 'CI')");
    jdbc.update(
        "INSERT INTO environment (id, repository_id, enabled, locked, name) "
            + "VALUES (1, 1, true, false, 'test-server')");

    insertRun(DEVELOP_NEWEST, 1, DEFAULT_BRANCH, "10 days");
    insertRun(DEVELOP_MIDDLE, 1, DEFAULT_BRANCH, "20 days");
    insertRun(DEVELOP_OLD, 1, DEFAULT_BRANCH, "120 days");
    insertRun(FEATURE_NEWEST, 1, FEATURE_BRANCH, "10 days");
    insertRun(FEATURE_MIDDLE, 1, FEATURE_BRANCH, "20 days");
    insertRun(FEATURE_OLD, 1, FEATURE_BRANCH, "30 days");
    insertRun(DEPLOYED_OLD, 1, "deployed-feature", "120 days");
    insertRun(HELIOS_DEPLOYED_OLD, 1, "helios-deployed-feature", "120 days");
    insertRun(NULL_BRANCH_NEWEST, 2, null, "10 days");
    insertRun(NULL_BRANCH_MIDDLE, 2, null, "20 days");
    insertRun(NULL_BRANCH_OLD, 2, null, "30 days");

    // A deployment references the old run → the max-age cap must preserve it.
    jdbc.update(
        "INSERT INTO deployment (id, environment_id, workflow_run_id, state) "
            + "VALUES (1, 1, ?, 'SUCCESS')",
        DEPLOYED_OLD);
    jdbc.update(
        "INSERT INTO helios_deployment (environment_id, triggering_user, status, "
            + "status_updated_at, created_at, updated_at, workflow_run_id) "
            + "VALUES (1, 'user', 'DEPLOYMENT_SUCCESS', now(), now(), now(), ?)",
        HELIOS_DEPLOYED_OLD);
  }

  private void insertRun(long id, long repositoryId, String headBranch, String ageInterval) {
    jdbc.update(
        "INSERT INTO workflow_run (id, run_attempt, run_number, workflow_id, repository_id, "
            + "head_branch, created_at) VALUES (?, 1, 1, ?, ?, ?, now() - INTERVAL '"
            + ageInterval
            + "')",
        id,
        repositoryId,
        repositoryId,
        headBranch);
  }

  @Test
  void maxAgeCapDeletesOldRunsIncludingDefaultBranchButPreservesDeployed() {
    int deleted =
        repo.purgeRunsOlderThan(90, WorkflowRunCleanupTask.MAX_AGE_CAP_BATCH_SIZE);

    // DEVELOP_OLD (120 days) goes despite being on the default branch; DEPLOYED_OLD and
    // HELIOS_DEPLOYED_OLD (also 120 days) stay because a deployment references them.
    assertThat(deleted).isEqualTo(1);
    assertThat(remainingRunIds())
        .containsExactlyInAnyOrder(
            DEVELOP_NEWEST, DEVELOP_MIDDLE,
            FEATURE_NEWEST, FEATURE_MIDDLE, FEATURE_OLD, DEPLOYED_OLD, HELIOS_DEPLOYED_OLD,
            NULL_BRANCH_NEWEST, NULL_BRANCH_MIDDLE, NULL_BRANCH_OLD);
  }

  @Test
  void maxAgeCapBatchSizeBoundsEachCall() {
    // With batch size 1, one call deletes exactly one of the eligible runs.
    assertThat(repo.purgeRunsOlderThan(90, 1)).isEqualTo(1);
    assertThat(repo.purgeRunsOlderThan(90, 1)).isZero();
  }

  @Test
  void countRunsOlderThanMatchesWhatTheCapWouldDelete() {
    assertThat(repo.countRunsOlderThan(90)).isEqualTo(1L);
    // All eleven runs are older than 5 days; the two deployment-referenced ones are excluded.
    assertThat(repo.countRunsOlderThan(5)).isEqualTo(9L);
  }

  private List<Long> remainingRunIds() {
    return jdbc.queryForList("SELECT id FROM workflow_run", Long.class);
  }
}
