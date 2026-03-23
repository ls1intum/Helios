package de.tum.cit.aet.helios.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.deployment.Deployment;
import de.tum.cit.aet.helios.deployment.LatestDeploymentUnion;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.releaseinfo.releasecandidate.ReleaseCandidateRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EnvironmentDtoTest {

  @Test
  void fromEnvironment_exposesWorkflowRunIdFromFallbackHeliosDeployment() {
    ReleaseCandidateRepository releaseCandidateRepository =
        mock(ReleaseCandidateRepository.class);
    when(releaseCandidateRepository.findByRepositoryRepositoryIdAndCommitSha(1L, "sha"))
        .thenReturn(List.of());

    GitRepository repository = new GitRepository();
    repository.setRepositoryId(1L);

    Environment environment = new Environment();
    environment.setId(1L);
    environment.setName("staging");
    environment.setRepository(repository);

    OffsetDateTime now = OffsetDateTime.now();

    Deployment deployment = new Deployment();
    deployment.setId(10L);
    deployment.setEnvironment(environment);
    deployment.setRepository(repository);
    deployment.setState(Deployment.State.IN_PROGRESS);
    deployment.setSha("sha");
    deployment.setRef("main");
    deployment.setTask("deploy");
    deployment.setCreatedAt(now);
    deployment.setUpdatedAt(now);

    HeliosDeployment heliosDeployment = new HeliosDeployment();
    heliosDeployment.setId(20L);
    heliosDeployment.setEnvironment(environment);
    heliosDeployment.setWorkflowRunId(123L);
    heliosDeployment.setWorkflowRunHtmlUrl("https://github.com/acme/repo/actions/runs/123");
    heliosDeployment.setCreatedAt(now.minusSeconds(5));
    heliosDeployment.setUpdatedAt(now);

    EnvironmentDto dto =
        EnvironmentDto.fromEnvironment(
            environment,
            LatestDeploymentUnion.realDeployment(deployment, heliosDeployment),
            Optional.empty(),
            releaseCandidateRepository);

    assertNotNull(dto.latestDeployment());
    assertEquals(123L, dto.latestDeployment().workflowRunId());
    assertEquals(
        "https://github.com/acme/repo/actions/runs/123",
        dto.latestDeployment().workflowRunHtmlUrl());
  }
}
