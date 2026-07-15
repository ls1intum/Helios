package de.tum.cit.aet.helios.workflow.cleanup;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
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
 * Integration test for the keep-N retention and max-age-cap native queries in
 * {@link WorkflowRunRepository}.
 *
 * <p>Runs against an embedded PostgreSQL (zonky) with the real Flyway schema, exercising the SQL
 * semantics a mocked-repository unit test cannot: the default-branch exemption join, the keep-N
 * window per ⟨repository, workflow, branch⟩, the hard age cap and the {@code ON DELETE CASCADE}
 * chain to {@code test_suite}/{@code test_case}.
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

  // develop: three runs, all outside any keep-2 window by age — keep-N must not touch them.
  private static final long DEVELOP_NEWEST = 100L;
  private static final long DEVELOP_MIDDLE = 101L;
  private static final long DEVELOP_OLD = 102L; // 120 days — only the max-age cap removes it
  // feature branch: three runs — keep-2 leaves the two newest, deletes the third.
  private static final long FEATURE_NEWEST = 110L;
  private static final long FEATURE_MIDDLE = 111L;
  private static final long FEATURE_OLD = 112L;

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
    jdbc.update("INSERT INTO workflow (id, repository_id, name) VALUES (1, 1, 'CI')");

    insertRun(DEVELOP_NEWEST, DEFAULT_BRANCH, "10 days");
    insertRun(DEVELOP_MIDDLE, DEFAULT_BRANCH, "20 days");
    insertRun(DEVELOP_OLD, DEFAULT_BRANCH, "120 days");
    insertRun(FEATURE_NEWEST, FEATURE_BRANCH, "10 days");
    insertRun(FEATURE_MIDDLE, FEATURE_BRANCH, "20 days");
    insertRun(FEATURE_OLD, FEATURE_BRANCH, "30 days");

    // Child rows under the run the max-age cap deletes, to prove the cascade.
    jdbc.update(
        "INSERT INTO test_suite (id, workflow_run_id, name, timestamp, tests, failures, errors, "
            + "skipped, time) VALUES (200, ?, 'suite', now(), 1, 0, 0, 0, 0.0)",
        DEVELOP_OLD);
    jdbc.update(
        "INSERT INTO test_case (id, test_suite_id, name, time, status) "
            + "VALUES (300, 200, 'case', 0.0, 'PASSED')");
  }

  private void insertRun(long id, String headBranch, String ageInterval) {
    jdbc.update(
        "INSERT INTO workflow_run (id, run_attempt, run_number, workflow_id, repository_id, "
            + "head_branch, created_at) VALUES (?, 1, 1, 1, 1, ?, now() - INTERVAL '"
            + ageInterval
            + "')",
        id,
        headBranch);
  }

  @Test
  void keepPolicyPrunesFeatureBranchButExemptsDefaultBranch() {
    int deleted = repo.purgeObsoleteRuns(2, 0, null);

    assertThat(deleted).isEqualTo(1);
    assertThat(remainingRunIds())
        .containsExactlyInAnyOrder(
            DEVELOP_NEWEST, DEVELOP_MIDDLE, DEVELOP_OLD, FEATURE_NEWEST, FEATURE_MIDDLE);
  }

  @Test
  void previewObsoleteRunIdsMatchesKeepPolicySemantics() {
    assertThat(repo.previewObsoleteRunIds(2, 0, null)).containsExactly(FEATURE_OLD);
  }

  @Test
  void maxAgeCapDeletesAnyRunOlderThanCapIncludingDefaultBranchAndCascades() {
    int deleted = repo.purgeRunsOlderThan(90);

    assertThat(deleted).isEqualTo(1);
    assertThat(remainingRunIds())
        .containsExactlyInAnyOrder(
            DEVELOP_NEWEST, DEVELOP_MIDDLE, FEATURE_NEWEST, FEATURE_MIDDLE, FEATURE_OLD);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM test_suite", Long.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM test_case", Long.class)).isZero();
  }

  @Test
  void countRunsOlderThanMatchesWhatTheCapWouldDelete() {
    assertThat(repo.countRunsOlderThan(90)).isEqualTo(1L);
    assertThat(repo.countRunsOlderThan(5)).isEqualTo(6L);
  }

  private java.util.List<Long> remainingRunIds() {
    return jdbc.queryForList("SELECT id FROM workflow_run", Long.class);
  }
}
