package de.tum.cit.aet.helios.deployment.approval;

import static org.assertj.core.api.Assertions.assertThat;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import java.time.OffsetDateTime;
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
 * Integration test for the queries behind the pending-approvals list and the stale-request sweep,
 * run against the real Flyway schema on an embedded PostgreSQL (zonky). It proves what a mocked
 * repository cannot: the 30-day cut-off in the list query, the state filter, and that the bulk
 * update archives only open rows that are older than the cut-off.
 */
@DataJpaTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureEmbeddedDatabase(
    type = AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES,
    provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.DOCKER)
@Import(DeploymentApprovalRequestRepositoryIntegrationTest.CacheTestConfig.class)
class DeploymentApprovalRequestRepositoryIntegrationTest {

  /** The {@code @DataJpaTest} slice does not load cache auto-config; supply a no-op manager. */
  @TestConfiguration
  static class CacheTestConfig {
    @Bean
    CacheManager cacheManager() {
      return new ConcurrentMapCacheManager();
    }
  }

  private static final long REVIEWER_ID = 10L;
  private static final List<DeploymentApprovalRequest.State> OPEN =
      List.of(
          DeploymentApprovalRequest.State.PENDING,
          DeploymentApprovalRequest.State.FAILED_AT_GITHUB);

  @Autowired private DeploymentApprovalRequestRepository repository;
  @Autowired private DataSource dataSource;
  private JdbcTemplate jdbc;

  @BeforeEach
  void seed() {
    jdbc = new JdbcTemplate(dataSource);
    jdbc.update(
        "INSERT INTO repository (repository_id, has_issues, has_projects, has_wiki, is_archived, "
            + "is_disabled, is_private, stargazers_count, watchers_count, name_with_owner) "
            + "VALUES (1, false, false, false, false, false, false, 0, 0, 'ls1intum/Helios')");
    jdbc.update(
        "INSERT INTO environment (id, repository_id, enabled, locked, name) "
            + "VALUES (1, 1, true, false, 'test-server')");
    jdbc.update(
        "INSERT INTO \"user\" (id, followers, following, login) VALUES (?, 0, 0, 'alice')",
        REVIEWER_ID);
    for (long id = 1; id <= 6; id++) {
      jdbc.update(
          "INSERT INTO helios_deployment (id, environment_id, created_at, status_updated_at, "
              + "updated_at, status, triggering_user) "
              + "VALUES (?, 1, now(), now(), now(), 'WAITING', 'alice')",
          id);
    }
    // 1: old FAILED_AT_GITHUB (the real-world leftover) - must disappear
    insertRequest(1, "FAILED_AT_GITHUB", "40 days");
    // 2: old PENDING - must disappear
    insertRequest(2, "PENDING", "31 days");
    // 3: recent PENDING - must stay
    insertRequest(3, "PENDING", "2 days");
    // 4: recent FAILED_AT_GITHUB - must stay (retry path)
    insertRequest(4, "FAILED_AT_GITHUB", "10 days");
    // 5: old but already APPROVED - never touched
    insertRequest(5, "APPROVED", "60 days");
    // 6: old DECLINED - never touched
    insertRequest(6, "DECLINED", "60 days");
  }

  private void insertRequest(long deploymentId, String state, String age) {
    jdbc.update(
        "INSERT INTO deployment_approval_request (helios_deployment_id, reviewer_id, "
            + "reviewer_login, state, via, created_at) "
            + "VALUES (?, ?, 'alice', ?, 'IN_APP', now() - INTERVAL '"
            + age
            + "')",
        deploymentId,
        REVIEWER_ID,
        state);
  }

  private String stateOf(long deploymentId) {
    return jdbc.queryForObject(
        "SELECT state FROM deployment_approval_request WHERE helios_deployment_id = ?",
        String.class,
        deploymentId);
  }

  @Test
  void pendingListHidesRequestsOlderThanGitHubsApprovalWindow() {
    var visible =
        repository.findByReviewerAndStateIn(
            REVIEWER_ID,
            OPEN,
            OffsetDateTime.now().minus(DeploymentApprovalRequest.GITHUB_APPROVAL_WINDOW));

    assertThat(visible)
        .extracting(r -> r.getHeliosDeployment().getId())
        .containsExactlyInAnyOrder(3L, 4L);
  }

  @Test
  void sweepArchivesOnlyOpenRequestsOlderThanTheCutoff() {
    OffsetDateTime now = OffsetDateTime.now();

    int archived =
        repository.expireOpenRequestsCreatedBefore(
            now.minus(DeploymentApprovalRequest.GITHUB_APPROVAL_WINDOW),
            now,
            StaleApprovalSweeper.EXPIRY_REASON,
            DeploymentApprovalRequest.State.EXPIRED,
            OPEN);

    assertThat(archived).isEqualTo(2);
    assertThat(stateOf(1)).isEqualTo("EXPIRED");
    assertThat(stateOf(2)).isEqualTo("EXPIRED");
    assertThat(stateOf(3)).isEqualTo("PENDING");
    assertThat(stateOf(4)).isEqualTo("FAILED_AT_GITHUB");
    assertThat(stateOf(5)).isEqualTo("APPROVED");
    assertThat(stateOf(6)).isEqualTo("DECLINED");
    assertThat(
            jdbc.queryForObject(
                "SELECT failure_reason FROM deployment_approval_request "
                    + "WHERE helios_deployment_id = 1",
                String.class))
        .isEqualTo(StaleApprovalSweeper.EXPIRY_REASON);
    assertThat(
            jdbc.queryForObject(
                "SELECT responded_at IS NOT NULL FROM deployment_approval_request "
                    + "WHERE helios_deployment_id = 1",
                Boolean.class))
        .isTrue();
  }
}
