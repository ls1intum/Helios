package de.tum.cit.aet.helios.auth.github.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Integration test for {@link GitHubUserTokenRepository} against the real Flyway schema (V59) on an
 * embedded PostgreSQL (zonky), exercising persistence, lookup by login, the {@code updated_at}
 * stamp, and the {@code github_login} unique constraint.
 */
@DataJpaTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureEmbeddedDatabase(
    type = AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES,
    provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.DOCKER)
@Import(GitHubUserTokenRepositoryIntegrationTest.CacheTestConfig.class)
class GitHubUserTokenRepositoryIntegrationTest {

  /** The {@code @DataJpaTest} slice does not load cache auto-config; supply a no-op manager. */
  @TestConfiguration
  static class CacheTestConfig {
    @Bean
    CacheManager cacheManager() {
      return new ConcurrentMapCacheManager();
    }
  }

  @Autowired private GitHubUserTokenRepository repository;

  @Test
  void savesAndFindsByGithubLoginAndStampsUpdatedAt() {
    GitHubUserToken token = new GitHubUserToken();
    token.setGithubLogin("octocat");
    token.setAccessTokenEnc("enc-access");
    token.setRefreshTokenEnc("enc-refresh");
    token.setAccessTokenExpiresAt(OffsetDateTime.now().plusHours(8));
    token.setRefreshTokenExpiresAt(OffsetDateTime.now().plusMonths(6));
    repository.saveAndFlush(token);

    assertThat(repository.findByGithubLogin("octocat")).isPresent();
    assertThat(repository.findByGithubLogin("octocat").get().getUpdatedAt()).isNotNull();
    assertThat(repository.findByGithubLogin("nobody")).isEmpty();
  }

  @Test
  void githubLoginIsUnique() {
    GitHubUserToken first = new GitHubUserToken();
    first.setGithubLogin("dup");
    repository.saveAndFlush(first);

    GitHubUserToken second = new GitHubUserToken();
    second.setGithubLogin("dup");
    assertThatThrownBy(() -> repository.saveAndFlush(second))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
