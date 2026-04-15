package de.tum.cit.aet.helios.workflow.detection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.workflow.Workflow;
import de.tum.cit.aet.helios.workflow.WorkflowRepository;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class WorkflowDeploymentJobDetectionServiceTest {

  @Mock private WorkflowRepository workflowRepository;
  @Mock private GitHubService gitHubService;
  @Mock private WorkflowDeploymentJobDetector workflowDeploymentJobDetector;

  private WorkflowDeploymentJobDetectionService service;

  @BeforeEach
  void setUp() {
    service =
        new WorkflowDeploymentJobDetectionService(
            workflowRepository, gitHubService, workflowDeploymentJobDetector);
  }

  @Test
  void detectDeploymentJobReturnsFoundResult() throws Exception {
    Workflow workflow = createWorkflow(11L, 5L);

    when(workflowRepository.findById(11L)).thenReturn(Optional.of(workflow));
    when(
            gitHubService.getRepositoryFileContent(
                "ls1intum/Helios", ".github/workflows/deploy.yml", "main"))
        .thenReturn("name: Deploy");
    when(
            workflowDeploymentJobDetector.detect(
                "Deploy", ".github/workflows/deploy.yml", "name: Deploy"))
        .thenReturn(
            new WorkflowDeploymentJobDetector.DetectionResult(
                "Deploy to staging",
                WorkflowDeploymentJobDetectionDto.Status.FOUND,
                "Detected deployment job successfully."));

    WorkflowDeploymentJobDetectionDto result = service.detectDeploymentJob(5L, 11L);

    assertEquals(11L, result.workflowId());
    assertEquals(".github/workflows/deploy.yml", result.workflowPath());
    assertEquals("main", result.ref());
    assertEquals("Deploy to staging", result.deploymentJobName());
    assertEquals(WorkflowDeploymentJobDetectionDto.Status.FOUND, result.status());
  }

  @Test
  void detectDeploymentJobReturnsErrorResultWhenGitHubFetchFails() throws Exception {
    Workflow workflow = createWorkflow(11L, 5L);

    when(workflowRepository.findById(11L)).thenReturn(Optional.of(workflow));
    when(
            gitHubService.getRepositoryFileContent(
                "ls1intum/Helios", ".github/workflows/deploy.yml", "main"))
        .thenThrow(new IOException("boom"));

    WorkflowDeploymentJobDetectionDto result = service.detectDeploymentJob(5L, 11L);

    assertEquals(WorkflowDeploymentJobDetectionDto.Status.ERROR, result.status());
    assertEquals("Helios could not analyze the workflow right now.", result.message());
  }

  @Test
  void detectDeploymentJobThrowsWhenWorkflowDoesNotBelongToRepository() {
    Workflow workflow = createWorkflow(11L, 9L);
    when(workflowRepository.findById(11L)).thenReturn(Optional.of(workflow));

    assertThrows(ResponseStatusException.class, () -> service.detectDeploymentJob(5L, 11L));
  }

  private Workflow createWorkflow(Long workflowId, Long repositoryId) {
    GitRepository repository = new GitRepository();
    repository.setRepositoryId(repositoryId);
    repository.setName("Helios");
    repository.setNameWithOwner("ls1intum/Helios");
    repository.setHtmlUrl("https://github.com/ls1intum/Helios");
    repository.setPushedAt(OffsetDateTime.now());
    repository.setVisibility(GitRepository.Visibility.PUBLIC);
    repository.setDefaultBranch("main");

    Workflow workflow = new Workflow();
    workflow.setId(workflowId);
    workflow.setName("Deploy");
    workflow.setPath(".github/workflows/deploy.yml");
    workflow.setState(Workflow.State.ACTIVE);
    workflow.setLabel(Workflow.Label.NONE);
    workflow.setRepository(repository);
    return workflow;
  }
}
