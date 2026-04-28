package de.tum.cit.aet.helios.environment;

import de.tum.cit.aet.helios.deployment.Deployment;
import de.tum.cit.aet.helios.deployment.DeploymentRepository;
import de.tum.cit.aet.helios.deployment.LatestDeploymentUnion;
import de.tum.cit.aet.helios.heliosdeployment.DeploymentDurationEstimate;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import de.tum.cit.aet.helios.releaseinfo.releasecandidate.ReleaseCandidate;
import de.tum.cit.aet.helios.releaseinfo.releasecandidate.ReleaseCandidateRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class EnvironmentListDtoBuilder {

  private record ReleaseCandidateKey(Long repositoryId, String commitSha) {}

  private final DeploymentRepository deploymentRepository;
  private final HeliosDeploymentRepository heliosDeploymentRepository;
  private final ReleaseCandidateRepository releaseCandidateRepository;

  List<EnvironmentDto> build(List<Environment> environments) {
    if (environments.isEmpty()) {
      return List.of();
    }

    List<Long> environmentIds = environments.stream().map(Environment::getId).toList();
    Map<Long, Deployment> latestDeployments = loadLatestDeployments(environmentIds);
    Map<Long, HeliosDeployment> latestHeliosDeployments =
        loadLatestHeliosDeployments(environmentIds);
    Map<Long, DeploymentDurationEstimate> estimates = loadDurationEstimates(environmentIds);
    Map<Long, LatestDeploymentUnion> latestByEnvironmentId =
        mapLatestDeployments(environments, latestDeployments, latestHeliosDeployments);
    Map<ReleaseCandidateKey, List<String>> releaseCandidateNames =
        loadReleaseCandidateNames(latestByEnvironmentId);

    return environments.stream()
        .map(
            environment -> {
              LatestDeploymentUnion latest = latestByEnvironmentId.get(environment.getId());
              return EnvironmentDto.fromEnvironmentSummary(
                  environment,
                  latest,
                  environment.getLatestStatus(),
                  releaseCandidateNamesFor(releaseCandidateNames, latest),
                  estimates.get(environment.getId()));
            })
        .toList();
  }

  private Map<Long, Deployment> loadLatestDeployments(List<Long> environmentIds) {
    return emptyIfNull(deploymentRepository.findLatestByEnvironmentIds(environmentIds)).stream()
        .collect(
            Collectors.toMap(
                deployment -> deployment.getEnvironment().getId(),
                Function.identity(),
                EnvironmentListDtoBuilder::newerDeployment));
  }

  private Map<Long, HeliosDeployment> loadLatestHeliosDeployments(List<Long> environmentIds) {
    return emptyIfNull(heliosDeploymentRepository.findLatestByEnvironmentIds(environmentIds))
        .stream()
        .collect(
            Collectors.toMap(
                deployment -> deployment.getEnvironment().getId(),
                Function.identity(),
                EnvironmentListDtoBuilder::newerHeliosDeployment));
  }

  private Map<Long, LatestDeploymentUnion> mapLatestDeployments(
      List<Environment> environments,
      Map<Long, Deployment> latestDeployments,
      Map<Long, HeliosDeployment> latestHeliosDeployments) {
    Map<Long, LatestDeploymentUnion> latestByEnvironmentId = new HashMap<>();
    environments.forEach(
        environment ->
            latestByEnvironmentId.put(
                environment.getId(),
                LatestDeploymentUnion.latest(
                    Optional.ofNullable(latestHeliosDeployments.get(environment.getId())),
                    Optional.ofNullable(latestDeployments.get(environment.getId())))));
    return latestByEnvironmentId;
  }

  private Map<Long, DeploymentDurationEstimate> loadDurationEstimates(List<Long> environmentIds) {
    Map<Long, DeploymentDurationEstimate> estimates = new HashMap<>();
    emptyIfNull(heliosDeploymentRepository.findMedianDurationsByEnvironmentIds(environmentIds))
        .forEach(
            row -> {
              if (row == null || row.length < 3 || row[0] == null || row[1] == null) {
                return;
              }
              Long environmentId = ((Number) row[0]).longValue();
              Double medianBuild = ((Number) row[1]).doubleValue();
              Double medianDeploy = row[2] != null ? ((Number) row[2]).doubleValue() : null;
              estimates.put(
                  environmentId, new DeploymentDurationEstimate(medianBuild, medianDeploy));
            });
    return estimates;
  }

  private Map<ReleaseCandidateKey, List<String>> loadReleaseCandidateNames(
      Map<Long, LatestDeploymentUnion> latestByEnvironmentId) {
    List<LatestDeploymentUnion> latestDeployments =
        latestByEnvironmentId.values().stream()
            .filter(latest -> latest != null && !latest.isNone())
            .filter(latest -> latest.getSha() != null)
            .toList();
    List<Long> repositoryIds =
        latestDeployments.stream()
            .map(latest -> latest.getEnvironment().getRepository().getRepositoryId())
            .distinct()
            .toList();
    List<String> commitShas =
        latestDeployments.stream().map(LatestDeploymentUnion::getSha).distinct().toList();

    if (repositoryIds.isEmpty() || commitShas.isEmpty()) {
      return Map.of();
    }

    return emptyIfNull(
            releaseCandidateRepository.findByRepositoryIdsAndCommitShas(repositoryIds, commitShas))
        .stream()
        .collect(
            Collectors.groupingBy(
                releaseCandidate ->
                    new ReleaseCandidateKey(
                        releaseCandidate.getRepository().getRepositoryId(),
                        releaseCandidate.getCommit().getSha()),
                Collectors.mapping(ReleaseCandidate::getName, Collectors.toList())));
  }

  private ReleaseCandidateKey releaseCandidateKey(LatestDeploymentUnion latest) {
    if (latest == null || latest.isNone() || latest.getSha() == null) {
      return null;
    }
    return new ReleaseCandidateKey(
        latest.getEnvironment().getRepository().getRepositoryId(), latest.getSha());
  }

  private List<String> releaseCandidateNamesFor(
      Map<ReleaseCandidateKey, List<String>> releaseCandidateNames, LatestDeploymentUnion latest) {
    ReleaseCandidateKey key = releaseCandidateKey(latest);
    return key != null ? releaseCandidateNames.getOrDefault(key, List.of()) : List.of();
  }

  private static Deployment newerDeployment(Deployment first, Deployment second) {
    if (second.getCreatedAt().isAfter(first.getCreatedAt())) {
      return second;
    }
    if (second.getCreatedAt().isEqual(first.getCreatedAt()) && second.getId() > first.getId()) {
      return second;
    }
    return first;
  }

  private static HeliosDeployment newerHeliosDeployment(
      HeliosDeployment first, HeliosDeployment second) {
    if (second.getCreatedAt().isAfter(first.getCreatedAt())) {
      return second;
    }
    if (second.getCreatedAt().isEqual(first.getCreatedAt()) && second.getId() > first.getId()) {
      return second;
    }
    return first;
  }

  private static <T> List<T> emptyIfNull(List<T> values) {
    return values != null ? values : List.of();
  }
}
