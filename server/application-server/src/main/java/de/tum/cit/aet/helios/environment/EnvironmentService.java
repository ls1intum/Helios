package de.tum.cit.aet.helios.environment;

import de.tum.cit.aet.helios.auth.AuthService;
import de.tum.cit.aet.helios.deployment.Deployment;
import de.tum.cit.aet.helios.deployment.DeploymentException;
import de.tum.cit.aet.helios.deployment.DeploymentRepository;
import de.tum.cit.aet.helios.deployment.LatestDeploymentUnion;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import de.tum.cit.aet.helios.user.User;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class EnvironmentService {

  private final AuthService authService;
  private final EnvironmentRepository environmentRepository;
  private final EnvironmentLockHistoryRepository lockHistoryRepository;
  private final HeliosDeploymentRepository heliosDeploymentRepository;
  private final DeploymentRepository deploymentRepository;

  public EnvironmentService(
      EnvironmentRepository environmentRepository,
      EnvironmentLockHistoryRepository lockHistoryRepository,
      HeliosDeploymentRepository heliosDeploymentRepository,
      AuthService authService,
      DeploymentRepository deploymentRepository) {
    this.environmentRepository = environmentRepository;
    this.lockHistoryRepository = lockHistoryRepository;
    this.heliosDeploymentRepository = heliosDeploymentRepository;
    this.authService = authService;
    this.deploymentRepository = deploymentRepository;
  }

  public Optional<EnvironmentDto> getEnvironmentById(Long id) {
    return environmentRepository.findById(id).map(EnvironmentDto::fromEnvironment);
  }

  public List<EnvironmentDto> getAllEnvironments() {
    return environmentRepository.findAllByOrderByNameAsc().stream()
        .map(
            env -> {
              LatestDeploymentUnion latest = findLatestDeployment(env);
              return EnvironmentDto.fromEnvironment(env, latest);
            })
        .collect(Collectors.toList());
  }

  public List<EnvironmentDto> getAllEnabledEnvironments() {
    return environmentRepository.findByEnabledTrueOrderByNameAsc().stream()
        .map(
            env -> {
              LatestDeploymentUnion latest = findLatestDeployment(env);
              return EnvironmentDto.fromEnvironment(env, latest);
            })
        .collect(Collectors.toList());
  }

  public List<EnvironmentDto> getEnvironmentsByRepositoryId(Long repositoryId) {
    return environmentRepository
        .findByRepositoryRepositoryIdOrderByCreatedAtDesc(repositoryId)
        .stream()
        .map(EnvironmentDto::fromEnvironment)
        .collect(Collectors.toList());
  }

  /**
   * Finds the "latest" deployment for the given environment by considering: 1) The most recent
   * HeliosDeployment (if present), ordered by `createdAt`. 2) If the HeliosDeployment has a
   * non-null `deploymentId`, the corresponding real Deployment is retrieved from the environment's
   * deployments. 3) If the HeliosDeployment has a null `deploymentId`, it is treated as a
   * placeholder. 4) If no HeliosDeployment exists, the latest real Deployment from the environment
   * is used.
   *
   * <p>The method compares the `updatedAt` timestamps of the latest HeliosDeployment and the latest
   * real Deployment to determine which one is the most recent. It returns a wrapper object
   * containing either the latest HeliosDeployment or the latest real Deployment.
   *
   * @param env The environment to search for deployments.
   * @return A wrapper object containing the latest Deployment or HeliosDeployment, or an empty
   *     result if no deployments exist.
   */
  private LatestDeploymentUnion findLatestDeployment(Environment env) {
    // Retrieve the latest HeliosDeployment
    Optional<HeliosDeployment> latestHeliosOpt =
        heliosDeploymentRepository.findTopByEnvironmentOrderByCreatedAtDesc(env);
    Optional<Deployment> latestDeploymentOpt =
        deploymentRepository.findFirstByEnvironmentIdOrderByCreatedAtDesc(env.getId());

    // If no deployments exist at all, return empty
    if (latestHeliosOpt.isEmpty() && latestDeploymentOpt.isEmpty()) {
      return LatestDeploymentUnion.none();
    }

    // Compare the latest HeliosDeployment and the latest real Deployment
    if (latestHeliosOpt.isPresent() && latestDeploymentOpt.isPresent()) {
      HeliosDeployment latestHelios = latestHeliosOpt.get();
      Deployment latestDeployment = latestDeploymentOpt.get();

      // Compare updatedAt timestamps to determine the latest
      if (latestDeployment.getCreatedAt().isAfter(latestHelios.getCreatedAt())) {
        return LatestDeploymentUnion.realDeployment(latestDeployment);
      } else {
        return LatestDeploymentUnion.heliosDeployment(latestHelios);
      }
    }

    // If only one of them exists, return the available one
    if (latestHeliosOpt.isPresent()) {
      return LatestDeploymentUnion.heliosDeployment(latestHeliosOpt.get());
    } else {
      return LatestDeploymentUnion.realDeployment(latestDeploymentOpt.get());
    }
  }

  /**
   * Locks the environment with the specified ID.
   *
   * <p>This method attempts to lock the environment by setting its locked status to true. If the
   * environment is already locked, it returns an empty Optional. If the environment is successfully
   * locked, it returns an Optional containing the locked environment.
   *
   * <p>This method is transactional and handles optimistic locking failures.
   *
   * @param id the ID of the environment to lock
   * @return an Optional containing the locked environment if successful, or an empty Optional if
   *     the environment is already locked or if an optimistic locking failure occurs
   * @throws EntityNotFoundException if no environment is found with the specified ID
   */
  @Transactional
  public Optional<Environment> lockEnvironment(Long id) {
    final User currentUser = authService.getUserFromGithubId();

    Environment environment =
        environmentRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Environment not found with ID: " + id));

    if (environment.isLocked()) {
      if (currentUser.equals(environment.getLockedBy())) {
        return Optional.of(environment);
      }

      return Optional.empty();
    }
    environment.setLockedBy(currentUser);
    environment.setLockedAt(OffsetDateTime.now());
    environment.setLocked(true);

    // Record lock event
    EnvironmentLockHistory history = new EnvironmentLockHistory();
    history.setEnvironment(environment);
    history.setLockedBy(currentUser);
    history.setLockedAt(OffsetDateTime.now());
    lockHistoryRepository.saveAndFlush(history);

    try {
      environmentRepository.save(environment);
    } catch (OptimisticLockingFailureException e) {
      // The environment was locked by another transaction
      return Optional.empty();
    }

    return Optional.of(environment);
  }

  /**
   * Unlocks the environment with the specified ID.
   *
   * <p>This method sets the locked status of the environment to false and saves the updated
   * environment.
   *
   * @param id the ID of the environment to unlock
   * @throws EntityNotFoundException if no environment is found with the specified ID
   */
  @Transactional
  public EnvironmentDto unlockEnvironment(Long id) {
    final User currentUser = authService.getUserFromGithubId();

    Environment environment =
        environmentRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Environment not found with ID: " + id));

    if (!environment.isLocked()) {
      throw new IllegalStateException("Environment is not locked");
    }

    if (!currentUser.equals(environment.getLockedBy())) {
      throw new SecurityException(
          "You do not have permission to unlock this environment. "
              + "Environment is locked by another user");
    }

    // 20 minutes timeout for redeployment
    if (!canUnlock(environment, 20)) {
      throw new DeploymentException("Deployment is still in progress, please wait.");
    }

    environment.setLocked(false);
    environment.setLockedBy(null);
    environment.setLockedAt(null);

    Optional<EnvironmentLockHistory> openLock =
        lockHistoryRepository.findLatestLockForEnvironmentAndUser(environment, currentUser);
    if (openLock.isPresent()) {
      EnvironmentLockHistory openLockHistory = openLock.get();
      openLockHistory.setUnlockedAt(OffsetDateTime.now());
      lockHistoryRepository.save(openLockHistory);
    }

    environmentRepository.save(environment);

    return EnvironmentDto.fromEnvironment(environment);
  }

  /**
   * Updates the environment with the specified ID.
   *
   * <p>This method updates the environment with the specified ID using the provided EnvironmentDto.
   *
   * @param id the ID of the environment to update
   * @param environmentDto the EnvironmentDto containing the updated environment information
   * @return an Optional containing the updated environment if successful, or an empty Optional if
   *     no environment is found with the specified ID
   * @throws EnvironmentException if the environment is locked and cannot be disabled
   */
  public Optional<EnvironmentDto> updateEnvironment(Long id, EnvironmentDto environmentDto)
      throws EnvironmentException {
    return environmentRepository
        .findById(id)
        .map(
            environment -> {
              if (!environmentDto.enabled() && environment.isLocked()) {
                throw new EnvironmentException(
                    "Environment is locked and can not be disabled. "
                        + "Please unlock the environment first.");
              } else if (!environment.isEnabled() && environmentDto.enabled()) {
                environment.setLocked(false);
                environment.setLockedBy(null);
                environment.setLockedAt(null);
              }
              environment.setEnabled(environmentDto.enabled());

              if (environmentDto.updatedAt() != null) {
                environment.setUpdatedAt(environmentDto.updatedAt());
              }
              if (environmentDto.installedApps() != null) {
                environment.setInstalledApps(environmentDto.installedApps());
              }
              if (environmentDto.description() != null) {
                environment.setDescription(environmentDto.description());
              }
              if (environmentDto.serverUrl() != null) {
                environment.setServerUrl(environmentDto.serverUrl());
              }

              environmentRepository.save(environment);
              return EnvironmentDto.fromEnvironment(environment);
            });
  }

  public EnvironmentLockHistoryDto getUsersCurrentLock() {
    final User currentUser = authService.getUserFromGithubId();
    Optional<EnvironmentLockHistory> lockHistory =
        lockHistoryRepository.findLatestLockForEnabledEnvironment(currentUser);

    return lockHistory.map(EnvironmentLockHistoryDto::fromEnvironmentLockHistory).orElse(null);
  }

  public List<EnvironmentLockHistoryDto> getLockHistoryByEnvironmentId(Long environmentId) {
    return lockHistoryRepository.findLockHistoriesByEnvironment(environmentId).stream()
        .map(EnvironmentLockHistoryDto::fromEnvironmentLockHistory)
        .collect(Collectors.toList());
  }

  private boolean canUnlock(Environment environment, long timeoutMinutes) {
    // Fetch the most recent deployment for the environment
    Optional<HeliosDeployment> latestDeployment =
        heliosDeploymentRepository.findTopByEnvironmentOrderByCreatedAtDesc(environment);

    if (latestDeployment.isEmpty()) {
      // No prior deployments, safe to unlock
      return true;
    }

    HeliosDeployment deployment = latestDeployment.get();

    // Check if timeout has elapsed
    if (deployment.getStatus() == HeliosDeployment.Status.IN_PROGRESS
        || deployment.getStatus() == HeliosDeployment.Status.WAITING
        || deployment.getStatus() == HeliosDeployment.Status.QUEUED) {
      OffsetDateTime now = OffsetDateTime.now();
      if (deployment.getStatusUpdatedAt().plusMinutes(timeoutMinutes).isAfter(now)) {
        return false;
      }

      deployment.setStatus(HeliosDeployment.Status.UNKNOWN);
      heliosDeploymentRepository.save(deployment);
    }
    return true;
  }
}
