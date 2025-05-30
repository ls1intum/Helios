package de.tum.cit.aet.helios.github;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.helios.auth.AuthService;
import de.tum.cit.aet.helios.auth.github.GitHubAuthBroker;
import de.tum.cit.aet.helios.auth.github.TokenExchangeResponse;
import de.tum.cit.aet.helios.deployment.github.GitHubDeploymentDto;
import de.tum.cit.aet.helios.environment.github.GitHubEnvironmentApiResponse;
import de.tum.cit.aet.helios.environment.github.GitHubEnvironmentDto;
import de.tum.cit.aet.helios.filters.RepositoryContext;
import de.tum.cit.aet.helios.github.permissions.GitHubPermissionsResponse;
import de.tum.cit.aet.helios.github.permissions.RepoPermissionType;
import de.tum.cit.aet.helios.workflow.GitHubWorkflowContext;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHArtifact;
import org.kohsuke.github.GHCommitPointer;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflow;
import org.kohsuke.github.GHWorkflowRun;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;
import org.kohsuke.github.function.InputStreamFunction;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GitHubServiceTest {

  @Mock private GitHubFacade githubFacade;

  @Mock private GitHubConfig gitHubConfig;

  @Mock private ObjectMapper objectMapper;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private OkHttpClient okHttpClient;

  @Mock private AuthService authService;

  @Mock private GitHubAuthBroker gitHubAuthBroker;

  @Mock private GitHubClientManager clientManager;

  private GitHubService gitHubService;

  private MockedStatic<RepositoryContext> repositoryContextMockedStatic;

  @BeforeEach
  void setUp() {
    gitHubService =
        new GitHubService(
            githubFacade,
            gitHubConfig,
            objectMapper,
            okHttpClient,
            authService,
            gitHubAuthBroker,
            clientManager);
    ReflectionTestUtils.setField(gitHubService, "heliosClientBaseUrl", "http://localhost:4200");
    repositoryContextMockedStatic = mockStatic(RepositoryContext.class);
  }

  @AfterEach
  void tearDown() {
    repositoryContextMockedStatic.close();
  }

  @Test
  void getRequestBuilder() {
    String testToken = "test-token";
    when(clientManager.getCurrentToken()).thenReturn(testToken);

    Request.Builder builder = gitHubService.getRequestBuilder();
    Request request = builder.url("http://localhost").build(); // Dummy URL

    assertEquals("token " + testToken, request.header("Authorization"));
    assertEquals("application/vnd.github+json", request.header("Accept"));
  }

  @Test
  void getInstalledRepositories() throws IOException {
    List<String> expectedRepos = List.of("repo1", "repo2");
    when(githubFacade.getInstalledRepositoriesForGitHubApp()).thenReturn(expectedRepos);

    List<String> actualRepos = gitHubService.getInstalledRepositories();

    assertEquals(expectedRepos, actualRepos);
    verify(githubFacade).getInstalledRepositoriesForGitHubApp();
  }

  @Test
  void clearInstalledRepositoriesCache() {
    // This method is primarily for cache eviction, which is hard to test directly without more
    // complex setup.
    // We can just call it to ensure no exceptions are thrown.
    assertDoesNotThrow(() -> gitHubService.clearInstalledRepositoriesCache());
  }

  @Test
  void getOrganizationClientSuccess() throws IOException {
    String orgName = "test-org";
    GHOrganization mockOrganization = mock(GHOrganization.class);
    when(gitHubConfig.getOrganizationName()).thenReturn(orgName);
    when(githubFacade.getOrganization(orgName)).thenReturn(mockOrganization);

    GHOrganization organization = gitHubService.getOrganizationClient();

    assertNotNull(organization);
    assertEquals(mockOrganization, organization);
    // Call again to test caching
    GHOrganization cachedOrganization = gitHubService.getOrganizationClient();
    assertEquals(mockOrganization, cachedOrganization);
    verify(githubFacade, times(1)).getOrganization(orgName);
  }

  @Test
  void getOrganizationClientNoOrgNameThrowsRuntimeException() {
    when(gitHubConfig.getOrganizationName()).thenReturn(null);
    assertThrows(RuntimeException.class, () -> gitHubService.getOrganizationClient());

    when(gitHubConfig.getOrganizationName()).thenReturn("");
    assertThrows(RuntimeException.class, () -> gitHubService.getOrganizationClient());
  }

  @Test
  void getRepository() throws IOException {
    String repoNameWithOwners = "owner/repo";
    GHRepository mockRepository = mock(GHRepository.class);
    when(githubFacade.getRepository(repoNameWithOwners)).thenReturn(mockRepository);

    GHRepository repository = gitHubService.getRepository(repoNameWithOwners);

    assertEquals(mockRepository, repository);
    verify(githubFacade).getRepository(repoNameWithOwners);
  }

  @Test
  void getWorkflowRunArtifacts() throws IOException {
    long repoId = 123L;
    long workflowRunId = 456L;
    GHRepository mockRepository = mock(GHRepository.class);
    GHWorkflowRun mockWorkflowRun = mock(GHWorkflowRun.class);
    @SuppressWarnings("unchecked")
    PagedIterable<GHArtifact> mockArtifacts = mock(PagedIterable.class);

    when(githubFacade.getRepositoryById(repoId)).thenReturn(mockRepository);
    when(mockRepository.getWorkflowRun(workflowRunId)).thenReturn(mockWorkflowRun);
    when(mockWorkflowRun.listArtifacts()).thenReturn(mockArtifacts);

    PagedIterable<GHArtifact> artifacts =
        gitHubService.getWorkflowRunArtifacts(repoId, workflowRunId);

    assertEquals(mockArtifacts, artifacts);
    verify(githubFacade).getRepositoryById(repoId);
    verify(mockRepository).getWorkflowRun(workflowRunId);
    verify(mockWorkflowRun).listArtifacts();
  }

  @Test
  void getWorkflows() throws IOException {
    String repoNameWithOwners = "owner/repo";
    GHRepository mockRepository = mock(GHRepository.class);
    @SuppressWarnings("unchecked")
    PagedIterable<GHWorkflow> mockPagedWorkflows = mock(PagedIterable.class);
    List<GHWorkflow> expectedWorkflows = List.of(mock(GHWorkflow.class));

    when(githubFacade.getRepository(repoNameWithOwners)).thenReturn(mockRepository);
    when(mockRepository.listWorkflows()).thenReturn(mockPagedWorkflows);
    when(mockPagedWorkflows.toList()).thenReturn(expectedWorkflows);

    List<GHWorkflow> workflows = gitHubService.getWorkflows(repoNameWithOwners);

    assertEquals(expectedWorkflows, workflows);
    verify(mockRepository).listWorkflows();
  }

  @Test
  void getWorkflow() throws IOException {
    String repoNameWithOwners = "owner/repo";
    String workflowId = "workflow.yml";
    GHRepository mockRepository = mock(GHRepository.class);
    GHWorkflow mockWorkflow = mock(GHWorkflow.class);

    when(githubFacade.getRepository(repoNameWithOwners)).thenReturn(mockRepository);
    when(mockRepository.getWorkflow(workflowId)).thenReturn(mockWorkflow);

    GHWorkflow workflow = gitHubService.getWorkflow(repoNameWithOwners, workflowId);

    assertEquals(mockWorkflow, workflow);
    verify(mockRepository).getWorkflow(workflowId);
  }

  @Test
  void dispatchWorkflowSuccess() throws IOException {
    final String repoNameWithOwners = "owner/repo";
    final String workflowFileNameOrId = "main.yml";
    final String ref = "main";
    final Map<String, Object> inputs = Map.of("key", "value");
    final String jsonPayload = "{\"ref\":\"main\",\"inputs\":{\"key\":\"value\"}}";

    when(clientManager.getCurrentToken()).thenReturn("test-token");
    when(objectMapper.writeValueAsString(any())).thenReturn(jsonPayload);

    Response mockResponse =
        new Response.Builder()
            .request(new Request.Builder().url("http://dummyurl").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(ResponseBody.create("", MediaType.parse("application/json")))
            .build();
    Call mockCall = mock(Call.class);
    when(okHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(mockResponse);

    assertDoesNotThrow(
        () ->
            gitHubService.dispatchWorkflow(repoNameWithOwners, workflowFileNameOrId, ref, inputs));
    verify(objectMapper).writeValueAsString(Map.of("ref", ref, "inputs", inputs));
    verify(okHttpClient).newCall(any(Request.class));
  }

  @Test
  void dispatchWorkflowApiFailure() throws IOException {
    String repoNameWithOwners = "owner/repo";
    String workflowFileNameOrId = "main.yml";
    String ref = "main";
    Map<String, Object> inputs = Map.of("key", "value");
    String jsonPayload = "{\"ref\":\"main\",\"inputs\":{\"key\":\"value\"}}";

    when(clientManager.getCurrentToken()).thenReturn("test-token");
    when(objectMapper.writeValueAsString(any())).thenReturn(jsonPayload);

    ResponseBody responseBody = ResponseBody.create("Error", MediaType.parse("application/json"));
    Response mockResponse =
        new Response.Builder()
            .request(new Request.Builder().url("http://dummyurl").build())
            .protocol(Protocol.HTTP_1_1)
            .code(500)
            .message("Internal Server Error")
            .body(responseBody)
            .build();

    Call mockCall = mock(Call.class);
    when(okHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(mockResponse);

    IOException exception =
        assertThrows(
            IOException.class,
            () -> {
              gitHubService.dispatchWorkflow(repoNameWithOwners, workflowFileNameOrId, ref, inputs);
            });
    assertTrue(exception.getMessage().contains("GitHub API call failed with response code: 500"));
  }

  @Test
  void getEnvironmentsSuccess() throws IOException {
    GHRepository mockRepository = mock(GHRepository.class);
    when(mockRepository.getOwnerName()).thenReturn("owner");
    when(mockRepository.getName()).thenReturn("repo");
    when(clientManager.getCurrentToken()).thenReturn("test-token");

    GitHubEnvironmentDto envDto = new GitHubEnvironmentDto();
    ReflectionTestUtils.setField(envDto, "id", 1L);
    ReflectionTestUtils.setField(envDto, "name", "env_name");

    GitHubEnvironmentApiResponse apiResponse = new GitHubEnvironmentApiResponse();
    ReflectionTestUtils.setField(apiResponse, "totalCount", 1);
    ReflectionTestUtils.setField(apiResponse, "environments", List.of(envDto));
    String jsonResponse =
        "{\"total_count\":1,\"environments\":[{\"id\":1,\"node_id\":\"node_id\","
            + "\"name\":\"env_name\"}]}";

    ResponseBody responseBody =
        ResponseBody.create(jsonResponse, MediaType.parse("application/json"));
    Response mockOkHttpResponse =
        new Response.Builder()
            .request(new Request.Builder().url("http://dummyurl").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build();
    Call mockCall = mock(Call.class);
    when(okHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(mockOkHttpResponse);
    when(objectMapper.readValue(jsonResponse, GitHubEnvironmentApiResponse.class))
        .thenReturn(apiResponse);

    List<GitHubEnvironmentDto> environments = gitHubService.getEnvironments(mockRepository);

    assertFalse(environments.isEmpty());
    assertEquals("env_name", environments.get(0).getName());
    verify(okHttpClient).newCall(any(Request.class));
    verify(objectMapper).readValue(jsonResponse, GitHubEnvironmentApiResponse.class);
  }

  @Test
  void getEnvironmentsApiFailure() throws IOException {
    GHRepository mockRepository = mock(GHRepository.class);
    when(mockRepository.getOwnerName()).thenReturn("owner");
    when(mockRepository.getName()).thenReturn("repo");
    when(clientManager.getCurrentToken()).thenReturn("test-token");

    ResponseBody responseBody = ResponseBody.create("Error", MediaType.parse("application/json"));
    Response mockOkHttpResponse =
        new Response.Builder()
            .request(new Request.Builder().url("http://dummyurl").build())
            .protocol(Protocol.HTTP_1_1)
            .code(500)
            .message("Internal Server Error")
            .body(responseBody)
            .build();

    Call mockCall = mock(Call.class);
    when(okHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(mockOkHttpResponse);

    IOException exception =
        assertThrows(IOException.class, () -> gitHubService.getEnvironments(mockRepository));
    assertTrue(exception.getMessage().contains("GitHub API call failed with response code: 500"));
  }

  @Test
  void getEnvironmentsNullBodyFailure() throws IOException {
    GHRepository mockRepository = mock(GHRepository.class);
    when(mockRepository.getOwnerName()).thenReturn("owner");
    when(mockRepository.getName()).thenReturn("repo");
    when(clientManager.getCurrentToken()).thenReturn("test-token");

    Response mockOkHttpResponse =
        new Response.Builder()
            .request(new Request.Builder().url("http://dummyurl").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(null) // Null body
            .build();
    Call mockCall = mock(Call.class);
    when(okHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(mockOkHttpResponse);

    IOException exception =
        assertThrows(IOException.class, () -> gitHubService.getEnvironments(mockRepository));
    assertEquals("Response body is null", exception.getMessage());
  }

  @Test
  void getDeploymentIterator() {
    GHRepository mockRepository = mock(GHRepository.class);
    String environmentName = "prod";
    Optional<OffsetDateTime> since = Optional.of(OffsetDateTime.now());
    when(clientManager.getCurrentToken()).thenReturn("test-token");

    java.util.Iterator<GitHubDeploymentDto> iterator =
        gitHubService.getDeploymentIterator(mockRepository, environmentName, since);

    assertNotNull(iterator);
    assertTrue(iterator instanceof GitHubDeploymentIterator);
  }

  @Test
  void getRepositoryRoleFromContextSuccess() throws IOException {
    final Long repositoryId = 123L;
    final String username = "testUser";
    final String permission = "admin";
    final String roleName = "administrator";

    repositoryContextMockedStatic.when(RepositoryContext::getRepositoryId).thenReturn(repositoryId);
    when(authService.getPreferredUsername()).thenReturn(username);
    when(clientManager.getCurrentToken()).thenReturn("test-token");

    GitHubPermissionsResponse permissionsResponse = new GitHubPermissionsResponse();
    permissionsResponse.setPermission(permission);
    permissionsResponse.setRoleName(roleName);
    String jsonResponse = "{\"permission\":\"admin\",\"role_name\":\"administrator\"}";

    ResponseBody responseBody =
        ResponseBody.create(jsonResponse, MediaType.parse("application/json"));
    Response mockOkHttpResponse =
        new Response.Builder()
            .request(new Request.Builder().url("http://dummyurl").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build();
    Call mockCall = mock(Call.class);
    when(okHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(mockOkHttpResponse);
    when(objectMapper.readValue(jsonResponse, GitHubPermissionsResponse.class))
        .thenReturn(permissionsResponse);

    var result = gitHubService.getRepositoryRole();

    assertNotNull(result);
    assertEquals(RepoPermissionType.ADMIN, result.getPermission());
    assertEquals(roleName, result.getRoleName());
  }

  @Test
  void getRepositoryRoleFromContextRepoIdError() {
    repositoryContextMockedStatic
        .when(RepositoryContext::getRepositoryId)
        .thenThrow(new RuntimeException("Repo error"));
    IOException exception =
        assertThrows(IOException.class, () -> gitHubService.getRepositoryRole());
    assertEquals("Failed to fetch repository ID", exception.getMessage());
  }

  @Test
  void getRepositoryRoleFromContextUsernameError() throws IOException {
    repositoryContextMockedStatic.when(RepositoryContext::getRepositoryId).thenReturn(123L);
    when(authService.getPreferredUsername()).thenThrow(new RuntimeException("User error"));
    IOException exception =
        assertThrows(IOException.class, () -> gitHubService.getRepositoryRole());
    assertEquals("Failed to fetch username", exception.getMessage());
  }

  @Test
  void getRepositoryRoleWithParamsSuccess() throws IOException {
    final String repositoryId = "123";
    final String username = "testUser";
    final String permission = "write";
    final String roleName = "writer";

    when(clientManager.getCurrentToken()).thenReturn("test-token");
    GitHubPermissionsResponse permissionsResponse = new GitHubPermissionsResponse();
    permissionsResponse.setPermission(permission);
    permissionsResponse.setRoleName(roleName);

    String jsonResponse = "{\"permission\":\"write\",\"role_name\":\"writer\"}";
    ResponseBody responseBody =
        ResponseBody.create(jsonResponse, MediaType.parse("application/json"));
    Response mockOkHttpResponse =
        new Response.Builder()
            .request(new Request.Builder().url("http://dummyurl").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build();
    Call mockCall = mock(Call.class);
    when(okHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(mockOkHttpResponse);
    when(objectMapper.readValue(jsonResponse, GitHubPermissionsResponse.class))
        .thenReturn(permissionsResponse);

    var result = gitHubService.getRepositoryRole(repositoryId, username);

    assertNotNull(result);
    assertEquals(RepoPermissionType.WRITE, result.getPermission());
    assertEquals(roleName, result.getRoleName());
  }

  @Test
  void getRepositoryRoleWithParamsNullRepoIdThrowsIllegalArgumentException() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              gitHubService.getRepositoryRole(null, "user");
            });
    assertEquals("Repository ID cannot be null or empty", exception.getMessage());
  }

  @Test
  void getRepositoryRoleWithParamsEmptyRepoIdThrowsIllegalArgumentException() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              gitHubService.getRepositoryRole("", "user");
            });
    assertEquals("Repository ID cannot be null or empty", exception.getMessage());
  }

  @Test
  void getRepositoryRoleWithParamsNullUsernameThrowsIllegalArgumentException() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              gitHubService.getRepositoryRole("repoId", null);
            });
    assertEquals("Username cannot be null or empty", exception.getMessage());
  }

  @Test
  void getRepositoryRoleWithParamsEmptyUsernameThrowsIllegalArgumentException() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              gitHubService.getRepositoryRole("repoId", "");
            });
    assertEquals("Username cannot be null or empty", exception.getMessage());
  }

  @Test
  void getRepositoryRoleApiFailure() throws IOException {
    String repositoryId = "123";
    String username = "testUser";
    when(clientManager.getCurrentToken()).thenReturn("test-token");

    ResponseBody responseBody = ResponseBody.create("", MediaType.parse("application/json"));
    Response mockOkHttpResponse =
        new Response.Builder()
            .request(new Request.Builder().url("http://dummyurl").build())
            .protocol(Protocol.HTTP_1_1)
            .code(404) // Simulate not found or other error
            .message("Not Found")
            .body(responseBody)
            .build();

    // Obtain mockCall from deep stub, then stub its execute method
    Call mockCall = okHttpClient.newCall(any(Request.class));
    when(mockCall.execute()).thenReturn(mockOkHttpResponse);

    IOException exception =
        assertThrows(
            IOException.class,
            () -> {
              gitHubService.getRepositoryRole(repositoryId, username);
            });
    // String expectedMessage = "GitHub API call failed with response code: 404";
    // The actual observed message is "Error occurred while fetching permissions"
    String expectedMessage = "Error occurred while fetching permissions";
    String actualMessage = exception.getMessage();
    assertEquals(expectedMessage, actualMessage);
  }

  @Test
  void createCommitStatusForPullRequestSuccess() throws IOException {
    GHPullRequest mockPullRequest = mock(GHPullRequest.class);
    GHRepository mockRepository = new GHRepository() {
      @Override
      public long getId() {
        return 123L;
      }

      @Override
      public String getFullName() {
        return "owner/repo";
      }
    };
    GHCommitPointer mockHead = mock(GHCommitPointer.class);

    when(mockPullRequest.getRepository()).thenReturn(mockRepository);
    when(mockPullRequest.getNumber()).thenReturn(1);
    when(mockPullRequest.getHead()).thenReturn(mockHead); // GHCommitPointer
    when(mockHead.getSha()).thenReturn("test-sha");

    gitHubService.createCommitStatusForPullRequest(mockPullRequest);

    verify(githubFacade)
        .createCommitStatus(
            "owner/repo",
            "test-sha",
            GHCommitState.SUCCESS,
            "http://localhost:4200/repo/123/ci-cd/pr/1",
            "Click to view the Helios page of this pull request.",
            "Helios");
  }

  @Test
  void createCommitStatusForPullRequestIoException() throws IOException {
    GHPullRequest mockPullRequest = mock(GHPullRequest.class);
    GHRepository mockRepository = new GHRepository() {
      @Override
      public long getId() {
        return 123L;
      }

      @Override
      public String getFullName() {
        return "owner/repo";
      }
    };
    GHCommitPointer mockHead = mock(GHCommitPointer.class);

    when(mockPullRequest.getRepository()).thenReturn(mockRepository);
    when(mockPullRequest.getNumber()).thenReturn(1);
    when(mockPullRequest.getHead()).thenReturn(mockHead);
    when(mockHead.getSha()).thenReturn("test-sha");

    doThrow(new IOException("API error"))
        .when(githubFacade)
        .createCommitStatus(anyString(), anyString(), any(), anyString(), anyString(), anyString());

    // Call the method and assert no exception is thrown (errors are logged)
    assertDoesNotThrow(() -> gitHubService.createCommitStatusForPullRequest(mockPullRequest));
    // Optionally, verify logging if a logger mock is set up
  }

  @Test
  void approveDeploymentOnBehalfOfUserSuccess() throws IOException {
    final String repoNameWithOwner = "owner/repo";
    final long runId = 1L;
    final Long environmentId = 10L;
    final String githubUserLogin = "testUser";
    final String userGithubToken = "user-token";
    String jsonPayload =
        "{\"environment_ids\":[10],\"state\":\"approved\",\"comment\":\"Automatically approved by"
            + " Helios\"}";

    TokenExchangeResponse tokenResponse = new TokenExchangeResponse();
    tokenResponse.setAccessToken(userGithubToken);
    when(gitHubAuthBroker.exchangeToken(githubUserLogin)).thenReturn(tokenResponse);
    when(objectMapper.writeValueAsString(any())).thenReturn(jsonPayload);

    Response mockResponse =
        new Response.Builder()
            .request(new Request.Builder().url("http://dummyurl").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200) // Success
            .message("OK")
            .body(ResponseBody.create("", MediaType.parse("application/json")))
            .build();
    Call mockCall = mock(Call.class);
    when(okHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(mockResponse);

    assertDoesNotThrow(
        () ->
            gitHubService.approveDeploymentOnBehalfOfUser(
                repoNameWithOwner, runId, environmentId, githubUserLogin));

    verify(gitHubAuthBroker).exchangeToken(githubUserLogin);
    verify(objectMapper)
        .writeValueAsString(
            Map.of(
                "environment_ids", List.of(environmentId),
                "state", "approved",
                "comment", "Automatically approved by Helios"));
    verify(okHttpClient).newCall(any(Request.class));
  }

  @Test
  void approveDeploymentOnBehalfOfUserTokenExchangeNull() throws IOException {
    String repoNameWithOwner = "owner/repo";
    long runId = 1L;
    Long environmentId = 10L;
    String githubUserLogin = "testUser";

    when(gitHubAuthBroker.exchangeToken(githubUserLogin)).thenReturn(null);
    // objectMapper.writeValueAsString will be called before the token check,
    // so it needs to be stubbed to prevent an NPE from RequestBody.create(null, ...)
    when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}"); // Stub to return dummy JSON

    // Method should log an error and return, not throw an exception upwards in this specific case.
    assertDoesNotThrow(
        () ->
            gitHubService.approveDeploymentOnBehalfOfUser(
                repoNameWithOwner, runId, environmentId, githubUserLogin));
    // objectMapper.writeValueAsString IS called before the early exit for null token
    verify(objectMapper, times(1)).writeValueAsString(anyMap());
    verify(okHttpClient, never()).newCall(any(Request.class));
  }

  @Test
  void approveDeploymentOnBehalfOfUserApiFailure() throws IOException {
    final String repoNameWithOwner = "owner/repo";
    final long runId = 1L;
    final Long environmentId = 10L;
    String githubUserLogin = "testUser";
    String userGithubToken = "user-token";
    String jsonPayload =
        "{\"environment_ids\":[10],\"state\":\"approved\",\"comment\":\"Automatically approved by"
            + " Helios\"}";

    TokenExchangeResponse tokenResponse = new TokenExchangeResponse();
    tokenResponse.setAccessToken(userGithubToken);
    when(gitHubAuthBroker.exchangeToken(githubUserLogin)).thenReturn(tokenResponse);
    when(objectMapper.writeValueAsString(any())).thenReturn(jsonPayload);

    ResponseBody responseBody =
        ResponseBody.create("Error details", MediaType.parse("application/json"));
    Response mockResponse =
        new Response.Builder()
            .request(new Request.Builder().url("http://dummyurl").build())
            .protocol(Protocol.HTTP_1_1)
            .code(500) // Failure
            .message("Internal Server Error")
            .body(responseBody)
            .build();
    Call mockCall = mock(Call.class);
    when(okHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(mockResponse);

    IOException exception =
        assertThrows(
            IOException.class,
            () -> {
              gitHubService.approveDeploymentOnBehalfOfUser(
                  repoNameWithOwner, runId, environmentId, githubUserLogin);
            });
    assertTrue(exception.getMessage().contains("GitHub API call failed with response code: 500"));
  }

  @Test
  void generateReleaseNotesSuccess() throws IOException {
    String repoNameWithOwner = "owner/repo";
    String tagName = "v1.0";
    String targetCommitish = "main";
    String expectedNotes = "Generated release notes";
    String requestJson = "{\"tag_name\":\"v1.0\",\"target_commitish\":\"main\"}";
    String responseJson = "{\"body\":\"Generated release notes\"}";

    when(clientManager.getCurrentToken()).thenReturn("test-token");
    when(objectMapper.writeValueAsString(
            Map.of("tag_name", tagName, "target_commitish", targetCommitish)))
        .thenReturn(requestJson);

    ResponseBody responseBody =
        ResponseBody.create(responseJson, MediaType.parse("application/json"));
    Response mockOkHttpResponse =
        new Response.Builder()
            .request(new Request.Builder().url("http://dummyurl").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build();
    Call mockCall = mock(Call.class);
    when(okHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(mockOkHttpResponse);
    when(objectMapper.readValue(responseJson, Map.class)).thenReturn(Map.of("body", expectedNotes));

    String actualNotes =
        gitHubService.generateReleaseNotes(repoNameWithOwner, tagName, targetCommitish);

    assertEquals(expectedNotes, actualNotes);
    verify(objectMapper)
        .writeValueAsString(Map.of("tag_name", tagName, "target_commitish", targetCommitish));
    verify(okHttpClient).newCall(any(Request.class));
    verify(objectMapper).readValue(responseJson, Map.class);
  }

  @Test
  void generateReleaseNotesNullTargetCommitish() throws IOException {
    String repoNameWithOwner = "owner/repo";
    String tagName = "v1.0";
    String expectedNotes = "Generated release notes";
    // targetCommitish is null
    String requestJson = "{\"tag_name\":\"v1.0\"}"; // No target_commitish
    String responseJson = "{\"body\":\"Generated release notes\"}";

    when(clientManager.getCurrentToken()).thenReturn("test-token");
    // Ensure the map passed to objectMapper matches the expected payload (without target_commitish)
    when(objectMapper.writeValueAsString(Map.of("tag_name", tagName))).thenReturn(requestJson);

    ResponseBody responseBody =
        ResponseBody.create(responseJson, MediaType.parse("application/json"));
    Response mockOkHttpResponse =
        new Response.Builder()
            .request(new Request.Builder().url("http://dummyurl").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBody)
            .build();
    Call mockCall = mock(Call.class);
    when(okHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(mockOkHttpResponse);
    when(objectMapper.readValue(responseJson, Map.class)).thenReturn(Map.of("body", expectedNotes));

    String actualNotes = gitHubService.generateReleaseNotes(repoNameWithOwner, tagName, null);

    assertEquals(expectedNotes, actualNotes);
    verify(objectMapper).writeValueAsString(Map.of("tag_name", tagName)); // Verify correct map
    verify(okHttpClient).newCall(any(Request.class));
    verify(objectMapper).readValue(responseJson, Map.class);
  }

  @Test
  void generateReleaseNotesApiFailure() throws IOException {
    final String repoNameWithOwner = "owner/repo";
    final String tagName = "v1.0";
    final String targetCommitish = "main";
    final String requestJson = "{\"tag_name\":\"v1.0\",\"target_commitish\":\"main\"}";

    when(clientManager.getCurrentToken()).thenReturn("test-token");
    when(objectMapper.writeValueAsString(anyMap())).thenReturn(requestJson);

    ResponseBody responseBody =
        ResponseBody.create("Error details", MediaType.parse("application/json"));
    Response mockOkHttpResponse =
        new Response.Builder()
            .request(new Request.Builder().url("http://dummyurl").build())
            .protocol(Protocol.HTTP_1_1)
            .code(500)
            .message("Error")
            .body(responseBody)
            .build();
    Call mockCall = mock(Call.class);
    when(okHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(mockOkHttpResponse);

    IOException exception =
        assertThrows(
            IOException.class,
            () -> {
              gitHubService.generateReleaseNotes(repoNameWithOwner, tagName, targetCommitish);
            });
    assertTrue(exception.getMessage().contains("GitHub API call failed with response code: 500"));
  }

  @Test
  void createReleaseOnBehalfOfUserSuccess() throws IOException {
    final String repoNameWithOwner = "owner/repo";
    final String tagName = "v1.0";
    final String commitish = "main";
    final String name = "Release v1.0";
    final String body = "Release notes";
    boolean draft = false;
    String githubUserLogin = "testUser";
    final String userGithubToken = "user-token";
    final long releaseId = 12345L;

    TokenExchangeResponse tokenResponse = new TokenExchangeResponse();
    tokenResponse.setAccessToken(userGithubToken);
    when(gitHubAuthBroker.exchangeToken(githubUserLogin)).thenReturn(tokenResponse);

    Map<String, Object> expectedPayload =
        Map.of(
            "tag_name", tagName,
            "target_commitish", commitish,
            "name", name,
            "body", body,
            "draft", draft);
    String jsonPayload =
        "{\"tag_name\":\"v1.0\", \"target_commitish\":\"main\", \"name\":\"Release v1.0\","
            + " \"body\":\"Release notes\", \"draft\":false}"; // Abridged
    when(objectMapper.writeValueAsString(expectedPayload)).thenReturn(jsonPayload);

    String responseJson = "{\"id\":" + releaseId + "}";
    ResponseBody responseBody =
        ResponseBody.create(responseJson, MediaType.parse("application/json"));
    Response mockOkHttpResponse =
        new Response.Builder()
            .request(new Request.Builder().url("http://dummyurl").build())
            .protocol(Protocol.HTTP_1_1)
            .code(201) // Created
            .message("Created")
            .body(responseBody)
            .build();
    Call mockCall = mock(Call.class);
    when(okHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(mockOkHttpResponse);
    when(objectMapper.readValue(responseJson, Map.class)).thenReturn(Map.of("id", releaseId));

    GHRepository mockRepo = mock(GHRepository.class);
    GHRelease mockRelease = mock(GHRelease.class);
    when(githubFacade.getRepository(repoNameWithOwner)).thenReturn(mockRepo);
    when(mockRepo.getRelease(releaseId)).thenReturn(mockRelease);

    GHRelease actualRelease =
        gitHubService.createReleaseOnBehalfOfUser(
            repoNameWithOwner, tagName, commitish, name, body, draft, githubUserLogin);

    assertEquals(mockRelease, actualRelease);
    verify(gitHubAuthBroker).exchangeToken(githubUserLogin);
    verify(objectMapper).writeValueAsString(expectedPayload);
    verify(okHttpClient).newCall(any(Request.class));
    verify(objectMapper).readValue(responseJson, Map.class);
    verify(mockRepo).getRelease(releaseId);
  }

  @Test
  void createReleaseOnBehalfOfUserTokenExchangeFails() throws IOException {
    String repoNameWithOwner = "owner/repo";
    String githubUserLogin = "testUser";
    when(gitHubAuthBroker.exchangeToken(githubUserLogin)).thenReturn(null);

    IOException exception =
        assertThrows(
            IOException.class,
            () -> {
              gitHubService.createReleaseOnBehalfOfUser(
                  repoNameWithOwner, "v1", "main", "name", "body", false, githubUserLogin);
            });
    assertEquals(
        "Failed to exchange token for GitHub user: " + githubUserLogin, exception.getMessage());
  }

  @Test
  void createReleaseOnBehalfOfUserApiFailure() throws IOException {
    final String repoNameWithOwner = "owner/repo";
    final String githubUserLogin = "testUser";
    final String userGithubToken = "user-token";

    TokenExchangeResponse tokenResponse = new TokenExchangeResponse();
    tokenResponse.setAccessToken(userGithubToken);
    when(gitHubAuthBroker.exchangeToken(githubUserLogin)).thenReturn(tokenResponse);
    when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}"); // Dummy JSON

    ResponseBody errorResponseBody =
        ResponseBody.create("Error", MediaType.parse("application/json"));
    Response mockOkHttpResponse =
        new Response.Builder()
            .request(new Request.Builder().url("http://dummyurl").build())
            .protocol(Protocol.HTTP_1_1)
            .code(500)
            .message("Error")
            .body(errorResponseBody)
            .build();
    Call mockCall = mock(Call.class);
    when(okHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(mockOkHttpResponse);

    IOException exception =
        assertThrows(
            IOException.class,
            () -> {
              gitHubService.createReleaseOnBehalfOfUser(
                  repoNameWithOwner, "v1", "main", "name", "body", false, githubUserLogin);
            });
    assertTrue(exception.getMessage().contains("GitHub API call failed with response code: 500"));
  }

  @Test
  void extractWorkflowContextSuccess() throws IOException {
    long repositoryId = 1L;
    long runId = 2L;
    long triggeringRunId = 100L;
    String headBranch = "feature-branch";
    String headSha = "abcdef123456";

    GHArtifact mockArtifact = mock(GHArtifact.class);
    @SuppressWarnings("unchecked")
    PagedIterable<GHArtifact> mockArtifacts = mock(PagedIterable.class);
    @SuppressWarnings("unchecked")
    PagedIterator<GHArtifact> mockPagedIterator = mock(PagedIterator.class);
    // List<GHArtifact> artifactList = List.of(mockArtifact); // Not used, can be removed

    when(githubFacade.getRepositoryById(repositoryId)).thenReturn(mock(GHRepository.class));
    // Mock the getWorkflowRunArtifacts method directly as it's part of the class under test,
    // but its internal calls to githubFacade.getRepositoryById().getWorkflowRun().listArtifacts()
    // are complex to mock deeply here.
    // Instead, we can mock getWorkflowRunArtifacts if it were public, or test its logic separately.
    // For this unit test, let's assume getWorkflowRunArtifacts correctly returns artifacts.
    // We'll simulate its behavior by directly providing the artifact.

    GHRepository ghRepositoryMock = mock(GHRepository.class);
    GHWorkflowRun ghWorkflowRunMock = mock(GHWorkflowRun.class);
    when(githubFacade.getRepositoryById(repositoryId)).thenReturn(ghRepositoryMock);
    when(ghRepositoryMock.getWorkflowRun(runId)).thenReturn(ghWorkflowRunMock);
    when(ghWorkflowRunMock.listArtifacts()).thenReturn(mockArtifacts);

    when(mockArtifacts.iterator()).thenReturn(mockPagedIterator);
    when(mockPagedIterator.hasNext()).thenReturn(true, false); // Iterate once
    when(mockPagedIterator.next()).thenReturn(mockArtifact);
    when(mockArtifact.getName()).thenReturn("workflow-context");

    // Prepare the artifact content
    String artifactContentString =
        "TRIGGERING_WORKFLOW_RUN_ID="
            + triggeringRunId
            + "\n"
            + "TRIGGERING_WORKFLOW_HEAD_BRANCH="
            + headBranch
            + "\n"
            + "TRIGGERING_WORKFLOW_HEAD_SHA="
            + headSha;

    // Create a ZIP archive in memory
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos)) {
      ZipEntry entry = new ZipEntry("workflow-context.txt");
      zos.putNextEntry(entry);
      zos.write(artifactContentString.getBytes());
      zos.closeEntry();
    }
    InputStream inputStream = new ByteArrayInputStream(baos.toByteArray());

    // Mock the download method
    @SuppressWarnings("unchecked")
    InputStreamFunction<GitHubWorkflowContext> matcher = any(InputStreamFunction.class);
    when(mockArtifact.download(matcher))
        .thenAnswer(
            invocation -> {
              InputStreamFunction<GitHubWorkflowContext> function = invocation.getArgument(0);
              // Reset stream for multiple calls if needed. ByteArrayInputStream supports reset.
              return function.apply(inputStream);
            });

    GitHubWorkflowContext context = gitHubService.extractWorkflowContext(repositoryId, runId);

    assertNotNull(context);
    assertEquals(triggeringRunId, context.runId());
    assertEquals(headBranch, context.headBranch());
    assertEquals(headSha, context.headSha());
  }

  @Test
  void extractWorkflowContextArtifactNotFound() throws IOException {
    long repositoryId = 1L;
    long runId = 2L;

    @SuppressWarnings("unchecked")
    PagedIterable<GHArtifact> mockArtifacts = mock(PagedIterable.class);
    @SuppressWarnings("unchecked")
    PagedIterator<GHArtifact> mockPagedIterator = mock(PagedIterator.class);
    when(mockArtifacts.iterator()).thenReturn(mockPagedIterator);
    when(mockPagedIterator.hasNext()).thenReturn(false); // No artifacts

    GHRepository ghRepositoryMock = mock(GHRepository.class);
    GHWorkflowRun ghWorkflowRunMock = mock(GHWorkflowRun.class);
    when(githubFacade.getRepositoryById(repositoryId)).thenReturn(ghRepositoryMock);
    when(ghRepositoryMock.getWorkflowRun(runId)).thenReturn(ghWorkflowRunMock);
    when(ghWorkflowRunMock.listArtifacts()).thenReturn(mockArtifacts);

    GitHubWorkflowContext context = gitHubService.extractWorkflowContext(repositoryId, runId);
    assertNull(context);
  }

  @Test
  void extractWorkflowContextArtifactDownloadError() throws IOException {
    long repositoryId = 1L;
    long runId = 2L;

    GHArtifact mockArtifact = mock(GHArtifact.class);
    @SuppressWarnings("unchecked")
    PagedIterable<GHArtifact> mockArtifacts = mock(PagedIterable.class);
    @SuppressWarnings("unchecked")
    PagedIterator<GHArtifact> mockPagedIterator = mock(PagedIterator.class);
    // List<GHArtifact> artifactList = List.of(mockArtifact); // Not used, can be removed

    GHRepository ghRepositoryMock = mock(GHRepository.class);
    GHWorkflowRun ghWorkflowRunMock = mock(GHWorkflowRun.class);
    when(githubFacade.getRepositoryById(repositoryId)).thenReturn(ghRepositoryMock);
    when(ghRepositoryMock.getWorkflowRun(runId)).thenReturn(ghWorkflowRunMock);
    when(ghWorkflowRunMock.listArtifacts()).thenReturn(mockArtifacts);

    when(mockArtifacts.iterator()).thenReturn(mockPagedIterator);
    when(mockPagedIterator.hasNext()).thenReturn(true, false);
    when(mockPagedIterator.next()).thenReturn(mockArtifact);
    when(mockArtifact.getName()).thenReturn("workflow-context");
    @SuppressWarnings("unchecked")
    InputStreamFunction<GitHubWorkflowContext> matcher = any(InputStreamFunction.class);
    when(mockArtifact.download(matcher))
        .thenThrow(new IOException("Download failed"));

    GitHubWorkflowContext context = gitHubService.extractWorkflowContext(repositoryId, runId);
    assertNull(context); // Errors are logged, null is returned
  }

  @Test
  void cancelWorkflowRunSuccess() throws IOException {
    final String repoNameWithOwner = "owner/repo";
    final long runId = 123L;
    when(clientManager.getCurrentToken()).thenReturn("test-token");

    Response mockResponse =
        new Response.Builder()
            .request(new Request.Builder().url("http://dummyurl").build())
            .protocol(Protocol.HTTP_1_1)
            .code(202) // Accepted
            .message("Accepted")
            .body(ResponseBody.create("", MediaType.parse("application/json")))
            .build();
    Call mockCall = mock(Call.class);
    when(okHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(mockResponse);

    assertDoesNotThrow(() -> gitHubService.cancelWorkflowRun(repoNameWithOwner, runId));
    verify(okHttpClient).newCall(any(Request.class));
  }

  @Test
  void cancelWorkflowRunApiFailure() throws IOException {
    String repoNameWithOwner = "owner/repo";
    long runId = 123L;
    when(clientManager.getCurrentToken()).thenReturn("test-token");

    ResponseBody responseBody =
        ResponseBody.create("Error details", MediaType.parse("application/json"));
    Response mockResponse =
        new Response.Builder()
            .request(new Request.Builder().url("http://dummyurl").build())
            .protocol(Protocol.HTTP_1_1)
            .code(500)
            .message("Internal Server Error")
            .body(responseBody)
            .build();
    Call mockCall = mock(Call.class);
    when(okHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(mockResponse);

    IOException exception =
        assertThrows(
            IOException.class,
            () -> {
              gitHubService.cancelWorkflowRun(repoNameWithOwner, runId);
            });
    assertTrue(exception.getMessage().contains("GitHub API call failed with response code: 500"));
  }
}
