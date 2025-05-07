package de.tum.cit.aet.helios.filters;

import de.tum.cit.aet.helios.gitreposettings.GitRepoSettings;
import de.tum.cit.aet.helios.gitreposettings.GitRepoSettingsRepository;
import de.tum.cit.aet.helios.gitreposettings.secret.RepoSecretService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
@Log4j2
public class RepoSecretFilter extends OncePerRequestFilter {

  private static final Pattern TOKEN =
      Pattern.compile("^repo-(\\d+)-([A-Za-z0-9_-]{43})$");

  private final GitRepoSettingsRepository gitRepoSettingsRepository;
  private final RepoSecretService secrets;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    // avoid parsing unrelated paths
    return !request.getRequestURI().startsWith("/api/environments/status");
  }

  @Override
  protected void doFilterInternal(HttpServletRequest req,
                                  @NotNull HttpServletResponse res,
                                  @NotNull FilterChain chain)
      throws IOException, ServletException {

    String header = req.getHeader("Authorization");
    if (header == null || !header.startsWith("Secret ")) {
      log.error("RepoSecretFilter: Missing Authorization header");
      res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing Authorization");
      return;
    }
    String token = header.substring(7).trim();
    Matcher m = TOKEN.matcher(token);
    if (!m.matches()) {
      log.error("RepoSecretFilter: Malformed token");
      res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Malformed token");
      return;
    }

    long repoId = Long.parseLong(m.group(1));
    GitRepoSettings settings =
        gitRepoSettingsRepository.findByRepositoryRepositoryId(repoId).orElse(null);
    if (settings == null || !secrets.matches(settings, token)) {
      log.error("RepoSecretFilter: Invalid secret for repo {}", repoId);
      res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid secret");
      return;
    }

    req.setAttribute("repository", settings);
    chain.doFilter(req, res);
  }
}
