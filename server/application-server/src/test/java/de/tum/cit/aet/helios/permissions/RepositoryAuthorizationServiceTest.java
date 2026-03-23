package de.tum.cit.aet.helios.permissions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.auth.AuthService;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.github.permissions.GitHubRepositoryRoleDto;
import de.tum.cit.aet.helios.github.permissions.RepoPermissionType;
import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RepositoryAuthorizationServiceTest {

  @Mock private AuthService authService;

  @Mock private GitHubService gitHubService;

  @InjectMocks private RepositoryAuthorizationService repositoryAuthorizationService;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(
        repositoryAuthorizationService, "heliosDevelopers", Set.of("helios-developer"));
  }

  @Test
  void hasAdminAccessReturnsTrueForHeliosDeveloper() {
    when(authService.getPreferredUsername()).thenReturn("helios-developer");

    boolean hasAdminAccess = repositoryAuthorizationService.hasAdminAccess(1L);

    assertTrue(hasAdminAccess);
    verifyNoInteractions(gitHubService);
  }

  @Test
  void hasAdminAccessReturnsTrueForRepositoryAdmin() throws Exception {
    when(authService.getPreferredUsername()).thenReturn("repo-admin");
    when(gitHubService.getRepositoryRole("1", "repo-admin"))
        .thenReturn(new GitHubRepositoryRoleDto(RepoPermissionType.ADMIN, "admin"));

    boolean hasAdminAccess = repositoryAuthorizationService.hasAdminAccess(1L);

    assertTrue(hasAdminAccess);
  }

  @Test
  void hasAdminAccessReturnsFalseForNonAdminPermission() throws Exception {
    when(authService.getPreferredUsername()).thenReturn("writer");
    when(gitHubService.getRepositoryRole("1", "writer"))
        .thenReturn(new GitHubRepositoryRoleDto(RepoPermissionType.WRITE, "write"));

    boolean hasAdminAccess = repositoryAuthorizationService.hasAdminAccess(1L);

    assertFalse(hasAdminAccess);
  }

  @Test
  void hasAdminAccessReturnsFalseWhenPermissionLookupFails() throws Exception {
    when(authService.getPreferredUsername()).thenReturn("repo-admin");
    when(gitHubService.getRepositoryRole("1", "repo-admin"))
        .thenThrow(new IOException("GitHub unavailable"));

    boolean hasAdminAccess = repositoryAuthorizationService.hasAdminAccess(1L);

    assertFalse(hasAdminAccess);
  }
}
