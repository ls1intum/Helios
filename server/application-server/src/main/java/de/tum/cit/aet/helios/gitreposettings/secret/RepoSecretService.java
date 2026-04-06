package de.tum.cit.aet.helios.gitreposettings.secret;

import de.tum.cit.aet.helios.gitreposettings.GitRepoSettings;
import de.tum.cit.aet.helios.gitreposettings.GitRepoSettingsRepository;
import jakarta.persistence.EntityNotFoundException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RepoSecretService {

  private static final SecureRandom RNG = new SecureRandom();
  // 256‑bit suffix
  private static final int RAW_BYTES = 32;

  private final GitRepoSettingsRepository gitRepoSettingsRepository;
  private final Argon2PasswordEncoder argon2;
  private final Semaphore argon2VerifySemaphore;

  public RepoSecretService(
      GitRepoSettingsRepository gitRepoSettingsRepository,
      @Value("${helios.repo-secret.argon2.salt-length:16}") int saltLength,
      @Value("${helios.repo-secret.argon2.hash-length:32}") int hashLength,
      @Value("${helios.repo-secret.argon2.parallelism:1}") int parallelism,
      @Value("${helios.repo-secret.argon2.memory-kib:16384}") int memoryKiB,
      @Value("${helios.repo-secret.argon2.iterations:2}") int iterations,
      @Value("${helios.repo-secret.max-concurrent-verifications:4}")
          int maxConcurrentVerifications) {
    this.gitRepoSettingsRepository = gitRepoSettingsRepository;
    this.argon2 = new Argon2PasswordEncoder(
        saltLength,
        hashLength,
        parallelism,
        memoryKiB,
        iterations);
    this.argon2VerifySemaphore = new Semaphore(Math.max(1, maxConcurrentVerifications), true);
  }

  /**
   * Generate or replace the repo’s shared secret.
   */
  @Transactional
  public String rotate(long repoId) {

    GitRepoSettings gitRepoSettings = gitRepoSettingsRepository.findByRepositoryRepositoryId(repoId)
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
    gitRepoSettingsRepository.save(gitRepoSettings);

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
    boolean acquired = false;
    try {
      // Bound concurrent Argon2 allocations to avoid heap spikes under request bursts.
      argon2VerifySemaphore.acquire();
      acquired = true;
      return argon2.matches(suffix, settings.getSecretHash());
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
      return false;
    } finally {
      if (acquired) {
        argon2VerifySemaphore.release();
      }
    }
  }
}
