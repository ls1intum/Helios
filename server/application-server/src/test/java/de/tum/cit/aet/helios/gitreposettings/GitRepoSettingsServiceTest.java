package de.tum.cit.aet.helios.gitreposettings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.environment.EnvironmentService;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class GitRepoSettingsServiceTest {

  @Mock private GitRepoSettingsRepository gitRepoRepository;
  @Mock private GitRepoRepository gitRepository;
  @Mock private EnvironmentService environmentService;

  @InjectMocks private GitRepoSettingsService gitRepoSettingsService;

  private GitRepository testGitRepository;
  private GitRepoSettings gitRepoSettings;
  private GitRepoSettingsDto gitRepoSettingsDto;

  @BeforeEach
  public void setUp() throws Exception {
    testGitRepository = new GitRepository();
    Field repoIdField = GitRepository.class.getDeclaredField("repositoryId");
    repoIdField.setAccessible(true);
    repoIdField.set(testGitRepository, 1L);

    gitRepoSettings = new GitRepoSettings();
    Field idField = GitRepoSettings.class.getDeclaredField("id");
    idField.setAccessible(true);
    idField.set(gitRepoSettings, 1L);
    Field repoField = gitRepoSettings.getClass().getSuperclass().getDeclaredField("repository");
    repoField.setAccessible(true);
    repoField.set(gitRepoSettings, testGitRepository);
    Field lockExpField = GitRepoSettings.class.getDeclaredField("lockExpirationThreshold");
    lockExpField.setAccessible(true);
    lockExpField.set(gitRepoSettings, 60L);
    Field lockResField = GitRepoSettings.class.getDeclaredField("lockReservationThreshold");
    lockResField.setAccessible(true);
    lockResField.set(gitRepoSettings, 30L);
    Field pkgField = GitRepoSettings.class.getDeclaredField("packageName");
    pkgField.setAccessible(true);
    pkgField.set(gitRepoSettings, "com.example.app");

    gitRepoSettingsDto = GitRepoSettingsDto.fromGitRepoSettings(gitRepoSettings);
  }

  @Test
  public void testGetExistingGitRepoSettings() throws Exception {
    when(gitRepoRepository.findByRepositoryRepositoryId(1L))
        .thenReturn(Optional.of(gitRepoSettings));

    Optional<GitRepoSettingsDto> result =
        gitRepoSettingsService.getOrCreateGitRepoSettingsByRepositoryId(1L);

    assertTrue(result.isPresent());
    assertEquals((Long) 1L, result.get().id());
    Field lockExpField = GitRepoSettings.class.getDeclaredField("lockExpirationThreshold");
    lockExpField.setAccessible(true);
    assertEquals(lockExpField.get(gitRepoSettings), result.get().lockExpirationThreshold());
    Field lockResField = GitRepoSettings.class.getDeclaredField("lockReservationThreshold");
    lockResField.setAccessible(true);
    assertEquals(lockResField.get(gitRepoSettings), result.get().lockReservationThreshold());
    Field pkgField = GitRepoSettings.class.getDeclaredField("packageName");
    pkgField.setAccessible(true);
    assertEquals(pkgField.get(gitRepoSettings), result.get().packageName());
    verify(gitRepoRepository, times(1)).findByRepositoryRepositoryId(1L);
  }

  @Test
  public void testCreateNewGitRepoSettings() {
    when(gitRepoRepository.findByRepositoryRepositoryId(1L)).thenReturn(Optional.empty());
    when(gitRepository.findByRepositoryId(1L)).thenReturn(Optional.of(testGitRepository));
    when(gitRepoRepository.save(any(GitRepoSettings.class))).thenReturn(gitRepoSettings);

    Optional<GitRepoSettingsDto> result =
        gitRepoSettingsService.getOrCreateGitRepoSettingsByRepositoryId(1L);

    assertTrue(result.isPresent());
    assertNotNull(result.get());
    verify(gitRepoRepository, times(1)).findByRepositoryRepositoryId(1L);
    verify(gitRepository, times(1)).findByRepositoryId(1L);
    verify(gitRepoRepository, times(1)).save(any(GitRepoSettings.class));
  }

  @Test
  public void testCreateNewGitRepoSettingsRepositoryNotFound() {
    when(gitRepoRepository.findByRepositoryRepositoryId(1L)).thenReturn(Optional.empty());
    when(gitRepository.findByRepositoryId(1L)).thenReturn(Optional.empty());

    assertThrows(
        IllegalArgumentException.class,
        () -> gitRepoSettingsService.getOrCreateGitRepoSettingsByRepositoryId(1L));

    verify(gitRepoRepository, times(1)).findByRepositoryRepositoryId(1L);
    verify(gitRepository, times(1)).findByRepositoryId(1L);
  }

  @Test
  public void testUpdateGitRepoSettings() {
    when(gitRepoRepository.findByRepositoryRepositoryId(1L))
        .thenReturn(Optional.of(gitRepoSettings));
    when(gitRepoRepository.save(any(GitRepoSettings.class))).thenReturn(gitRepoSettings);

    GitRepoSettingsDto updateDto = new GitRepoSettingsDto(1L, 120L, 60L, "com.example.updated");

    Optional<GitRepoSettingsDto> result =
        gitRepoSettingsService.updateGitRepoSettings(1L, updateDto);

    assertTrue(result.isPresent());
    assertEquals(updateDto.lockExpirationThreshold(), result.get().lockExpirationThreshold());
    assertEquals(updateDto.lockReservationThreshold(), result.get().lockReservationThreshold());
    assertEquals(updateDto.packageName(), result.get().packageName());
    verify(gitRepoRepository, times(1)).findByRepositoryRepositoryId(1L);
    verify(gitRepoRepository, times(1)).save(any(GitRepoSettings.class));
    verify(environmentService, times(1)).updateLockExpirationAndReservation(1L);
  }

  @Test
  public void testUpdateGitRepoSettingsNotFound() {
    when(gitRepoRepository.findByRepositoryRepositoryId(1L)).thenReturn(Optional.empty());

    GitRepoSettingsDto updateDto = new GitRepoSettingsDto(1L, 120L, 60L, "com.example.updated");

    assertThrows(
        IllegalArgumentException.class,
        () -> gitRepoSettingsService.updateGitRepoSettings(1L, updateDto));

    verify(gitRepoRepository, times(1)).findByRepositoryRepositoryId(1L);
  }

  @Test
  public void testUpdateGitRepoSettingsPartialUpdate() throws Exception {
    when(gitRepoRepository.findByRepositoryRepositoryId(1L))
        .thenReturn(Optional.of(gitRepoSettings));
    when(gitRepoRepository.save(any(GitRepoSettings.class))).thenReturn(gitRepoSettings);

    // Only update lockExpirationThreshold
    GitRepoSettingsDto updateDto = new GitRepoSettingsDto(1L, 120L, null, null);

    Optional<GitRepoSettingsDto> result =
        gitRepoSettingsService.updateGitRepoSettings(1L, updateDto);

    assertTrue(result.isPresent());
    Field lockResField = GitRepoSettings.class.getDeclaredField("lockReservationThreshold");
    lockResField.setAccessible(true);
    assertEquals(lockResField.get(gitRepoSettings), result.get().lockReservationThreshold());
    Field pkgField = GitRepoSettings.class.getDeclaredField("packageName");
    pkgField.setAccessible(true);
    assertEquals(pkgField.get(gitRepoSettings), result.get().packageName());
    verify(gitRepoRepository, times(1)).findByRepositoryRepositoryId(1L);
    verify(gitRepoRepository, times(1)).save(any(GitRepoSettings.class));
    verify(environmentService, times(1)).updateLockExpirationAndReservation(1L);
  }
}
