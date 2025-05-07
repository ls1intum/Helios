package de.tum.cit.aet.helios.gitreposettings.secret;

import de.tum.cit.aet.helios.gitreposettings.GitRepoSettings;
import de.tum.cit.aet.helios.gitreposettings.GitRepoSettingsRepository;
import jakarta.persistence.EntityNotFoundException;
import java.security.SecureRandom;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RepoSecretService {

  private static final SecureRandom RNG = new SecureRandom();
  // 256‑bit suffix
  private static final int RAW_BYTES = 32;
  private final GitRepoSettingsRepository repo;

  /**
   * Argon2id encoder – allocate once, reuse.
   */
  private final Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder(
      16,      // salt length (bytes) – internal
      32,      // hash length (bytes)
      1,       // parallelism
      1 << 16, // memory = 65 536 KiB
      3);      // iterations

  /**
   * Generate or replace the repo’s shared secret.
   */
  @Transactional
  public String rotate(long repoId) {

    GitRepoSettings gitRepoSettings = repo.findById(repoId)
        .orElseThrow(() -> new EntityNotFoundException("Repo " + repoId + " not found!"));

    /* 256‑bit random suffix */
    byte[] raw = new byte[RAW_BYTES];
    RNG.nextBytes(raw);
    String suffix = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(raw); // 43 chars

    /* Only show the clean token once, do not store it */
    String token = "repo-%d-%s".formatted(repoId, suffix);

    /* Argon2‑hash the suffix; the returned string embeds its own salt */
    String hash = argon2.encode(suffix);

    /* 4) Persist the hash (overwriting any previous value) */
    gitRepoSettings.setSecretHash(hash);
    repo.save(gitRepoSettings);

    return token;
  }

  /**
   * True iff token belongs to repo and suffix matches stored hash.
   */
  public boolean matches(GitRepoSettings settings, String token) {
    // "repo", "<id>", "<suffix>"
    String[] parts = token.split("-", 3);
    if (parts.length != 3) {
      return false;
    }

    long repoId = Long.parseLong(parts[1]);
    if (repoId != settings.getRepository().getRepositoryId()) {
      // wrong repository
      return false;
    }

    String suffix = parts[2];
    return argon2.matches(suffix, settings.getSecretHash());
  }
}
