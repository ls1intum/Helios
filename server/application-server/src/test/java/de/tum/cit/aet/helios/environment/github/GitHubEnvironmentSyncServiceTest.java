package de.tum.cit.aet.helios.environment.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.environment.EnvironmentRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GitHubEnvironmentSyncServiceTest {

  @Mock private EnvironmentRepository environmentRepository;

  @InjectMocks private GitHubEnvironmentSyncService syncService;

  @Test
  void shouldDeleteEnvironmentNotInGitHub() {
    GitHubEnvironmentDto env1 = createDto(1L);
    GitHubEnvironmentDto env2 = createDto(2L);
    Environment localEnv3 = createEnvironment(3L, "env3");

    when(environmentRepository.findByRepositoryRepositoryIdOrderByCreatedAtDesc(100L))
        .thenReturn(
            List.of(createEnvironment(1L, "env1"), createEnvironment(2L, "env2"), localEnv3));

    syncService.removeDeletedEnvironments(List.of(env1, env2), 100L);

    ArgumentCaptor<Environment> captor = ArgumentCaptor.forClass(Environment.class);
    verify(environmentRepository, times(1)).delete(captor.capture());
    assertEquals(3L, captor.getValue().getId());
  }

  @Test
  void shouldNotDeleteWhenAllExistInGitHub() {
    GitHubEnvironmentDto env1 = createDto(1L);
    GitHubEnvironmentDto env2 = createDto(2L);

    when(environmentRepository.findByRepositoryRepositoryIdOrderByCreatedAtDesc(100L))
        .thenReturn(List.of(createEnvironment(1L, "env1"), createEnvironment(2L, "env2")));

    syncService.removeDeletedEnvironments(List.of(env1, env2), 100L);

    verify(environmentRepository, never()).delete(any());
  }

  @Test
  void shouldDeleteMultipleMissingEnvironments() {
    GitHubEnvironmentDto env1 = createDto(1L);

    when(environmentRepository.findByRepositoryRepositoryIdOrderByCreatedAtDesc(100L))
        .thenReturn(
            List.of(
                createEnvironment(1L, "env1"),
                createEnvironment(2L, "env2"),
                createEnvironment(3L, "env3")));

    syncService.removeDeletedEnvironments(List.of(env1), 100L);

    ArgumentCaptor<Environment> captor = ArgumentCaptor.forClass(Environment.class);
    verify(environmentRepository, times(2)).delete(captor.capture());
    assertEquals(List.of(2L, 3L), captor.getAllValues().stream().map(Environment::getId).toList());
  }

  @Test
  void shouldDeleteAllWhenGitHubListIsEmpty() {
    when(environmentRepository.findByRepositoryRepositoryIdOrderByCreatedAtDesc(100L))
        .thenReturn(List.of(createEnvironment(1L, "env1"), createEnvironment(2L, "env2")));

    syncService.removeDeletedEnvironments(List.of(), 100L);

    verify(environmentRepository, times(2)).delete(any());
  }

  @Test
  void shouldNotDeleteWhenLocalListIsEmpty() {
    GitHubEnvironmentDto env1 = createDto(1L);

    when(environmentRepository.findByRepositoryRepositoryIdOrderByCreatedAtDesc(100L))
        .thenReturn(List.of());

    syncService.removeDeletedEnvironments(List.of(env1), 100L);

    verify(environmentRepository, never()).delete(any());
  }

  private GitHubEnvironmentDto createDto(Long id) {
    GitHubEnvironmentDto dto = new GitHubEnvironmentDto();
    ReflectionTestUtils.setField(dto, "id", id);
    return dto;
  }

  private Environment createEnvironment(Long id, String name) {
    Environment env = new Environment();
    env.setId(id);
    env.setName(name);
    return env;
  }
}
