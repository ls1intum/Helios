package de.tum.cit.aet.helios.auth.github.token;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GitHubUserTokenRepository extends JpaRepository<GitHubUserToken, Long> {

  Optional<GitHubUserToken> findByGithubLogin(String githubLogin);
}
