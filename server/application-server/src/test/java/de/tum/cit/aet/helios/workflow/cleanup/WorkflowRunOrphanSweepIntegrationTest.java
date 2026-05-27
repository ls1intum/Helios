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
 * Integration test for the orphan-branch sweep native queries in {@link WorkflowRunRepository}.
 *
 * <p>Runs against an embedded PostgreSQL (zonky) with the real Flyway schema, so it exercises the
 * actual SQL semantics that a mocked-repository unit test cannot: the branch-existence join, the
 * grace window, {@code NULL head_branch} handling, the deployment exclusion, and the
 * {@code ON DELETE CASCADE} chain to {@code test_suite}/{@code test_case}.
 */
@DataJpaTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureEmbeddedDatabase(
    type = AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES,
    provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.DOCKER)
@Import(WorkflowRunOrphanSweepIntegrationTest.CacheTestConfig.class)
class WorkflowRunOrphanSweepIntegrationTest {

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

  private static final int GRACE_DAYS = 7;
  private static final int BATCH_SIZE = 5000;

  private static final long ORPHAN_OLD = 100L; // deleted branch, older than grace → DELETE
  private static final long LIVE_BRANCH = 101L; // branch still exists → keep
  private static final long ORPHAN_YOUNG = 102L; // deleted branch but within grace → keep
  private static final long NULL_BRANCH = 103L; // no head_branch → keep
  // orphan branch, but referenced by a deployment → keep
  private static final long ORPHAN_WITH_DEPLOYMENT = 104L;

  @Autowired private WorkflowRunRepository repo;
  @Autowired private DataSource dataSource;
  private JdbcTemplate jdbc;

  @BeforeEach
  void seed() {
    jdbc = new JdbcTemplate(dataSource);

    jdbc.update(
        "INSERT INTO repository (repository_id, has_issues, has_projects, has_wiki, is_archived, "
            + "is_disabled, is_private, stargazers_count, watchers_count, name_with_owner) "
            + "VALUES (1, false, false, false, false, false, false, 0, 0, 'ls1intum/Helios')");
    jdbc.update("INSERT INTO workflow (id, repository_id, name) VALUES (1, 1, 'CI')");
    jdbc.update(
        "INSERT INTO environment (id, repository_id, enabled, locked, name) "
            + "VALUES (1, 1, true, false, 'test-server')");
    // The only branch that still exists.
    jdbc.update(
        "INSERT INTO branch (repository_id, name, ahead_by, behind_by, is_default, protection) "
            + "VALUES (1, 'main', 0, 0, true, false)");

    insertRun(ORPHAN_OLD, "deleted-feature", "30 days");
    insertRun(LIVE_BRANCH, "main", "30 days");
    insertRun(ORPHAN_YOUNG, "deleted-feature", "1 day");
    insertRun(NULL_BRANCH, null, "30 days");
    insertRun(ORPHAN_WITH_DEPLOYMENT, "deleted-feature", "30 days");

    // A deployment references the orphan run → the sweep must preserve it.
    jdbc.update(
        "INSERT INTO deployment (id, environment_id, workflow_run_id, state) "
            + "VALUES (1, 1, ?, 'SUCCESS')",
        ORPHAN_WITH_DEPLOYMENT);

    // Child rows under the deletable orphan run, to prove the cascade.
    jdbc.update(
        "INSERT INTO test_suite (id, workflow_run_id, name, timestamp, tests, failures, errors, "
            + "skipped, time) VALUES (200, ?, 'suite', now(), 1, 0, 0, 0, 0.0)",
        ORPHAN_OLD);
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
  void countCountsOnlyOldOrphansExcludingLiveBranchYoungNullAndDeploymentReferenced() {
    assertThat(repo.countOrphanBranchRunIds(GRACE_DAYS)).isEqualTo(1L);
  }

  @Test
  void previewReturnsOnlyTheDeletableOrphan() {
    assertThat(repo.previewOrphanBranchRunIds(GRACE_DAYS, BATCH_SIZE)).containsExactly(ORPHAN_OLD);
  }

  @Test
  void purgeDeletesOnlyTheOrphanAndCascadesItsChildren() {
    int deleted = repo.purgeOrphanBranchRunsBatch(GRACE_DAYS, BATCH_SIZE);

    assertThat(deleted).isEqualTo(1);
    assertThat(remainingRunIds())
        .containsExactlyInAnyOrder(LIVE_BRANCH, ORPHAN_YOUNG, NULL_BRANCH, ORPHAN_WITH_DEPLOYMENT);
    // ON DELETE CASCADE removed the orphan's test suite and case.
    assertThat(count("test_suite", "workflow_run_id = " + ORPHAN_OLD)).isZero();
    assertThat(count("test_case", "test_suite_id = 200")).isZero();
  }

  @Test
  void purgeRespectsBatchSizeLimit() {
    // batchSize 2 must delete at most 2 rows even though only one is eligible here.
    assertThat(repo.purgeOrphanBranchRunsBatch(GRACE_DAYS, 2)).isEqualTo(1);
  }

  private List<Long> remainingRunIds() {
    return jdbc.queryForList("SELECT id FROM workflow_run ORDER BY id", Long.class);
  }

  private int count(String table, String where) {
    return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + where, Integer.class);
  }
}
