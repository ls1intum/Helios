package de.tum.cit.aet.helios.filters;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.environment.EnvironmentRepository;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

/**
 * Verifies Helios's per-repository tenant isolation: the Hibernate {@code gitRepositoryFilter}
 * must actually scope a web-request query to the current repository.
 *
 * <p>This guards the regression that shipped in 1.7.0 and was fixed in 1.7.2: after the
 * {@code RepositoryFilterAspect -> RepositoryFilterTransactionConfig} refactor, the filter was no
 * longer enabled for web reads (under Open-Session-In-View the request transaction reuses the
 * pre-opened EntityManager, so the transaction-manager initializer never fired), and every tenant
 * query leaked across all repositories. {@link RepositoryInterceptor} now enables the filter on
 * the request's EntityManager; this test drives that exact path against a real embedded Postgres
 * and the real Flyway schema, so a future change that stops the filter from being enabled — or
 * breaks the {@code @Filter} definition — fails here instead of silently leaking data.
 *
 * <p>Scope note: the servlet-level ordering that guarantees the EntityManager is bound before the
 * interceptor runs (i.e. that OSIV runs first) is a wiring concern verified in staging/prod, not
 * reproduced here; this test binds the EntityManager via the surrounding {@code @DataJpaTest}
 * transaction, exactly as OSIV would at request time.
 */
@DataJpaTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureEmbeddedDatabase(
    type = AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES,
    provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.DOCKER)
@Import(RepositoryTenantFilterIntegrationTest.CacheTestConfig.class)
class RepositoryTenantFilterIntegrationTest {

  /** The @DataJpaTest slice does not load cache auto-configuration; provide a simple one. */
  @TestConfiguration
  static class CacheTestConfig {
    @Bean
    CacheManager cacheManager() {
      return new ConcurrentMapCacheManager();
    }
  }

  private static final long REPO_A = 1L;
  private static final long REPO_B = 2L;

  @Autowired private EnvironmentRepository environmentRepository;
  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private DataSource dataSource;

  private RepositoryInterceptor interceptor;

  @BeforeEach
  void seed() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    insertRepository(jdbc, REPO_A, "ls1intum/repo-a");
    insertRepository(jdbc, REPO_B, "ls1intum/repo-b");
    // Two enabled environments in repo A, one in repo B.
    insertEnabledEnvironment(jdbc, 1L, REPO_A, "repo-a-staging");
    insertEnabledEnvironment(jdbc, 2L, REPO_A, "repo-a-production");
    insertEnabledEnvironment(jdbc, 3L, REPO_B, "repo-b-production");
    interceptor = new RepositoryInterceptor(entityManagerFactory);
  }

  @AfterEach
  void clearContext() {
    RepositoryContext.clear();
  }

  @Test
  void scopesEnabledEnvironmentsToRepositoryA() {
    enterWebRequestForRepository(REPO_A);

    List<Environment> environments = environmentRepository.findByEnabledTrueOrderByNameAsc();

    assertThat(environments)
        .extracting(env -> env.getRepository().getRepositoryId())
        .as("only repo A's environments must be visible while scoped to repo A")
        .containsExactly(REPO_A, REPO_A);
  }

  @Test
  void scopesEnabledEnvironmentsToRepositoryB() {
    enterWebRequestForRepository(REPO_B);

    List<Environment> environments = environmentRepository.findByEnabledTrueOrderByNameAsc();

    assertThat(environments)
        .extracting(env -> env.getRepository().getRepositoryId())
        .as("only repo B's environment must be visible while scoped to repo B")
        .containsExactly(REPO_B);
  }

  @Test
  void withoutRepositoryContextTheFilterIsNotEnabled() {
    // No X-REPOSITORY-ID header -> no repository context -> filter stays off, matching how
    // global (non-repo-scoped) requests behave. Proves the filter — not some other mechanism — is
    // what scopes the tenant queries; if this returned only one repo, scoping would be an
    // accident of the data rather than the filter.
    List<Environment> environments = environmentRepository.findByEnabledTrueOrderByNameAsc();

    assertThat(environments)
        .extracting(env -> env.getRepository().getRepositoryId())
        .containsExactlyInAnyOrder(REPO_A, REPO_A, REPO_B);
  }

  /** Reproduces what {@link RepositoryInterceptor} does at the start of a web request. */
  private void enterWebRequestForRepository(long repositoryId) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RepositoryInterceptor.X_REPOSITORY_ID, String.valueOf(repositoryId));
    interceptor.preHandle(new ServletWebRequest(request));
  }

  private static void insertRepository(JdbcTemplate jdbc, long id, String nameWithOwner) {
    jdbc.update(
        "INSERT INTO repository (repository_id, has_issues, has_projects, has_wiki, is_archived, "
            + "is_disabled, is_private, stargazers_count, watchers_count, name_with_owner) "
            + "VALUES (?, false, false, false, false, false, false, 0, 0, ?)",
        id,
        nameWithOwner);
  }

  private static void insertEnabledEnvironment(
      JdbcTemplate jdbc, long id, long repositoryId, String name) {
    jdbc.update(
        "INSERT INTO environment (id, repository_id, enabled, locked, name) "
            + "VALUES (?, ?, true, false, ?)",
        id,
        repositoryId,
        name);
  }
}
