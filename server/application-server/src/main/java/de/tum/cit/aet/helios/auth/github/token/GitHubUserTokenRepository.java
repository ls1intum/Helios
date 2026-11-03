package de.tum.cit.aet.helios.auth.github.token;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GitHubUserTokenRepository extends JpaRepository<GitHubUserToken, Long> {

  Optional<GitHubUserToken> findByGithubLogin(String githubLogin);

  /**
   * Takes a transaction-scoped Postgres advisory lock for {@code login}. Concurrent callers for the
   * same login block until the holder's transaction ends, across all application instances. Works
   * even when no row exists yet, which a row lock could not.
   */
  @Query(
      value = "select count(*) from (select pg_advisory_xact_lock(hashtext(:login))) as locked",
      nativeQuery = true)
  long lockLogin(@Param("login") String login);
}
