package de.tum.cit.aet.helios.gitreposettings.secret;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.gitreposettings.GitRepoSettings;
import de.tum.cit.aet.helios.gitreposettings.GitRepoSettingsRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@Disabled("Temporarily disabled in CI while stabilizing repo-secret verification tests")
class RepoSecretServiceTest {

  @Mock private GitRepoSettingsRepository gitRepoSettingsRepository;

  private RepoSecretService repoSecretService;

  @BeforeEach
  void setUp() {
    repoSecretService =
        new RepoSecretService(
            gitRepoSettingsRepository,
            16,
            32,
            1,
            4096,
            3,
            4);
  }

  @Test
  void matches_acceptsLegacyHighMemoryHashes() {
    long repositoryId = 69562331L;
    String suffix = "abcdefghijklmnopqrstuvwxyzABCDEFGH1234567";
    String token = "repo-%d-%s".formatted(repositoryId, suffix);

    // Legacy prod setting: memory = 65_536 KiB.
    Argon2PasswordEncoder legacyEncoder = new Argon2PasswordEncoder(16, 32, 1, 1 << 16, 3);
    String legacyHash = legacyEncoder.encode(suffix);

    GitRepoSettings settings = createSettings(repositoryId, legacyHash);

    assertTrue(repoSecretService.matches(settings, token));
  }

  @Test
  void rotate_usesConfiguredMemorySettingAndTokenStaysValid() {
    long repositoryId = 42L;
    GitRepoSettings settings = createSettings(
        repositoryId, "$argon2id$v=19$m=4096,t=3,p=1$dummy$dummy");

    when(gitRepoSettingsRepository.findByRepositoryRepositoryId(repositoryId))
        .thenReturn(Optional.of(settings));
    when(gitRepoSettingsRepository.save(any(GitRepoSettings.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    String token = repoSecretService.rotate(repositoryId);

    assertNotNull(token);
    assertTrue(token.startsWith("repo-42-"));
    assertEquals(51, token.length());
    assertTrue(settings.getSecretHash().contains("m=4096"));
    assertTrue(repoSecretService.matches(settings, token));

    verify(gitRepoSettingsRepository).save(settings);
  }

  private static GitRepoSettings createSettings(long repositoryId, String secretHash) {
    GitRepository repository = new GitRepository();
    repository.setRepositoryId(repositoryId);

    GitRepoSettings settings = new GitRepoSettings();
    settings.setRepository(repository);
    settings.setSecretHash(secretHash);
    return settings;
  }
}
