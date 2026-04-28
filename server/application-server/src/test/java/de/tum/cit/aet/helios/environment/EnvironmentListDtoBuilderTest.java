package de.tum.cit.aet.helios.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.commit.Commit;
import de.tum.cit.aet.helios.deployment.Deployment;
import de.tum.cit.aet.helios.deployment.DeploymentRepository;
import de.tum.cit.aet.helios.deployment.LatestDeploymentUnion.DeploymentType;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import de.tum.cit.aet.helios.releaseinfo.releasecandidate.ReleaseCandidate;
import de.tum.cit.aet.helios.releaseinfo.releasecandidate.ReleaseCandidateRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EnvironmentListDtoBuilderTest {

  @Mock private DeploymentRepository deploymentRepository;
  @Mock private HeliosDeploymentRepository heliosDeploymentRepository;
  @Mock private ReleaseCandidateRepository releaseCandidateRepository;

  private EnvironmentListDtoBuilder builder;

  @BeforeEach
  void setUp() {
    builder =
        new EnvironmentListDtoBuilder(
            deploymentRepository, heliosDeploymentRepository, releaseCandidateRepository);
  }

  @Test
  void buildReturnsEmptyListWithoutQueryingRelatedData() {
    assertTrue(builder.build(List.of()).isEmpty());

    verifyNoInteractions(
        deploymentRepository, heliosDeploymentRepository, releaseCandidateRepository);
  }

  @Test
  void buildUsesBatchedDataForLatestDeploymentDetails() {
    GitRepository repository = repository(1L);
    Environment environment = environment(10L, repository);
    OffsetDateTime now = OffsetDateTime.now();
    Deployment deployment = deployment(100L, environment, repository, "sha-new", now);
    HeliosDeployment heliosDeployment =
        heliosDeployment(200L, environment, "sha-old", now.minusMinutes(5));
    ReleaseCandidate releaseCandidate = releaseCandidate(repository, "sha-new", "v1.0.0");

    when(deploymentRepository.findLatestByEnvironmentIds(List.of(10L)))
        .thenReturn(List.of(deployment));
    when(heliosDeploymentRepository.findLatestByEnvironmentIds(List.of(10L)))
        .thenReturn(List.of(heliosDeployment));
    when(heliosDeploymentRepository.findMedianDurationsByEnvironmentIds(List.of(10L)))
        .thenReturn(List.<Object[]>of(new Object[] {10L, 42.4, 13.2}));
    when(releaseCandidateRepository.findByRepositoryIdsAndCommitShas(
            List.of(1L), List.of("sha-new")))
        .thenReturn(List.of(releaseCandidate));

    List<EnvironmentDto> result = builder.build(List.of(environment));

    EnvironmentDto dto = result.getFirst();
    assertEquals(10L, dto.id());
    assertEquals(1L, dto.repository().id());
    assertTrue(dto.repository().contributors().isEmpty());
    assertEquals(DeploymentType.GITHUB, dto.latestDeployment().type());
    assertEquals("sha-new", dto.latestDeployment().sha());
    assertEquals(List.of("v1.0.0"), dto.latestDeployment().releaseCandidateNames());
    assertEquals(42, dto.latestDeployment().estimatedBuildDurationSeconds());
    assertEquals(13, dto.latestDeployment().estimatedDeployDurationSeconds());

    verify(deploymentRepository).findLatestByEnvironmentIds(List.of(10L));
    verify(heliosDeploymentRepository).findLatestByEnvironmentIds(List.of(10L));
    verify(heliosDeploymentRepository).findMedianDurationsByEnvironmentIds(List.of(10L));
    verify(releaseCandidateRepository)
        .findByRepositoryIdsAndCommitShas(List.of(1L), List.of("sha-new"));
  }

  @Test
  void buildHandlesMissingRelatedDataAsEmpty() {
    Environment environment = environment(10L, repository(1L));

    List<EnvironmentDto> result = builder.build(List.of(environment));

    assertEquals(1, result.size());
    assertNull(result.getFirst().latestDeployment());
  }

  private GitRepository repository(Long id) {
    GitRepository repository = new GitRepository();
    repository.setRepositoryId(id);
    repository.setName("repo-" + id);
    repository.setNameWithOwner("owner/repo-" + id);
    repository.setHtmlUrl("https://github.com/owner/repo-" + id);
    repository.setUpdatedAt(OffsetDateTime.now());
    return repository;
  }

  private Environment environment(Long id, GitRepository repository) {
    Environment environment = new Environment();
    environment.setId(id);
    environment.setName("env-" + id);
    environment.setRepository(repository);
    environment.setEnabled(true);
    environment.setType(Environment.Type.TEST);
    return environment;
  }

  private Deployment deployment(
      Long id,
      Environment environment,
      GitRepository repository,
      String sha,
      OffsetDateTime createdAt) {
    Deployment deployment = new Deployment();
    deployment.setId(id);
    deployment.setEnvironment(environment);
    deployment.setRepository(repository);
    deployment.setSha(sha);
    deployment.setRef("main");
    deployment.setState(Deployment.State.SUCCESS);
    deployment.setCreatedAt(createdAt);
    deployment.setUpdatedAt(createdAt);
    return deployment;
  }

  private HeliosDeployment heliosDeployment(
      Long id, Environment environment, String sha, OffsetDateTime createdAt) {
    HeliosDeployment deployment = new HeliosDeployment();
    deployment.setId(id);
    deployment.setEnvironment(environment);
    deployment.setSha(sha);
    deployment.setBranchName("feature");
    deployment.setStatus(HeliosDeployment.Status.IN_PROGRESS);
    deployment.setCreatedAt(createdAt);
    deployment.setUpdatedAt(createdAt);
    return deployment;
  }

  private ReleaseCandidate releaseCandidate(
      GitRepository repository, String commitSha, String name) {
    Commit commit = new Commit();
    commit.setRepository(repository);
    commit.setSha(commitSha);

    ReleaseCandidate releaseCandidate = new ReleaseCandidate();
    releaseCandidate.setRepository(repository);
    releaseCandidate.setCommit(commit);
    releaseCandidate.setName(name);
    return releaseCandidate;
  }
}
