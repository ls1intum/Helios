package de.tum.cit.aet.helios.environment;

import de.tum.cit.aet.helios.auth.AuthService;
import de.tum.cit.aet.helios.deployment.Deployment;
import de.tum.cit.aet.helios.deployment.DeploymentException;
import de.tum.cit.aet.helios.deployment.DeploymentRepository;
import de.tum.cit.aet.helios.deployment.LatestDeploymentUnion;
import de.tum.cit.aet.helios.gitreposettings.GitRepoSettingsDto;
import de.tum.cit.aet.helios.gitreposettings.GitRepoSettingsService;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import de.tum.cit.aet.helios.releasecandidate.ReleaseCandidateRepository;
import de.tum.cit.aet.helios.user.User;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class EnvironmentService {

  private final AuthService authService;
  private final EnvironmentRepository environmentRepository;
  private final EnvironmentLockHistoryRepository lockHistoryRepository;
  private final HeliosDeploymentRepository heliosDeploymentRepository;
  private final ReleaseCandidateRepository releaseCandidateRepository;
  private final DeploymentRepository deploymentRepository;
  @Lazy private final GitRepoSettingsService gitRepoSettingsService;
  private final EnvironmentScheduler environmentScheduler;

  public EnvironmentService(
      AuthService authService,
      EnvironmentRepository environmentRepository,
      EnvironmentLockHistoryRepository lockHistoryRepository,
      HeliosDeploymentRepository heliosDeploymentRepository,
      ReleaseCandidateRepository releaseCandidateRepository,
      DeploymentRepository deploymentRepository,
      @Lazy GitRepoSettingsService gitRepoSettingsService,
      EnvironmentScheduler environmentScheduler) {
    this.authService = authService;
    this.environmentRepository = environmentRepository;
    this.lockHistoryRepository = lockHistoryRepository;
    this.heliosDeploymentRepository = heliosDeploymentRepository;
    this.releaseCandidateRepository = releaseCandidateRepository;
    this.deploymentRepository = deploymentRepository;
    this.gitRepoSettingsService = gitRepoSettingsService;
    this.environmentScheduler = environmentScheduler;
  }

  public Optional<EnvironmentDto> getEnvironmentById(Long id) {
    return environmentRepository.findById(id).map(EnvironmentDto::fromEnvironment);
  }

  public Optional<Environment.Type> getEnvironmentTypeById(Long id) {
    return environmentRepository.findById(id).map(Environment::getType);
  }

  public List<EnvironmentDto> getAllEnvironments() {
    return environmentRepository.findAllByOrderByNameAsc().stream()
        .map(
            environment -> {
              environmentScheduler.unlockExpiredEnvironments();
              LatestDeploymentUnion latest = findLatestDeployment(environment);
              return EnvironmentDto.fromEnvironment(
                  environment, latest, environment.getLatestStatus(), releaseCandidateRepository);
            })
        .collect(Collectors.toList());
  }

  public List<EnvironmentDto> getAllEnabledEnvironments() {
    return environmentRepository.findByEnabledTrueOrderByNameAsc().stream()
        .map(
            environment -> {
              environmentScheduler.unlockExpiredEnvironments();
              LatestDeploymentUnion latest = findLatestDeployment(environment);
              return EnvironmentDto.fromEnvironment(
                  environment, latest, environment.getLatestStatus(), releaseCandidateRepository);
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
  public LatestDeploymentUnion findLatestDeployment(Environment env) {
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
      // TODO: add logs and check what's returned in ehre
      // Compare updatedAt timestamps to determine the latest
      if (latestDeployment.getCreatedAt().isAfter(latestHelios.getCreatedAt())
          || latestDeployment.getCreatedAt().isEqual(latestHelios.getCreatedAt())) {
        return LatestDeploymentUnion.realDeployment(latestDeployment, latestHelios);
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

    if (!environment.isEnabled()) {
      throw new IllegalStateException("Environment is disabled");
    }

    // Only proceed with locking if it's a TEST environment
    if (environment.getType() != Environment.Type.TEST) {
      // Return the environment without locking for non-TEST environments
      return Optional.of(environment);
    }

    if (environment.isLocked()) {
      if (currentUser.equals(environment.getLockedBy())) {
        return Optional.of(environment);
      }
      return Optional.empty();
    }

    environment.setLockedBy(currentUser);
    environment.setLockedAt(OffsetDateTime.now());
    environment.setLocked(true);
    environment.setLockWillExpireAt(getLockWillExpireAt(environment));
    environment.setLockReservationExpiresAt(getLockReservationExpiresAt(environment));

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

  @Transactional
  private OffsetDateTime getLockWillExpireAt(Environment environment) {
    Long lockExpirationThreshold =
        environment.getLockExpirationThreshold() != null
            ? environment.getLockExpirationThreshold()
            : gitRepoSettingsService
                .getOrCreateGitRepoSettingsByRepositoryId(
                    environment.getRepository().getRepositoryId())
                .map(GitRepoSettingsDto::lockExpirationThreshold)
                .orElse(-1L);
    if (environment.isLocked() && environment.getLockedAt() != null) {
      return lockExpirationThreshold != -1
          ? environment.getLockedAt().plusMinutes(lockExpirationThreshold)
          : null;
    } else {
      return null;
    }
  }

  @Transactional
  private OffsetDateTime getLockReservationExpiresAt(Environment environment) {
    Long lockReservationThreshold =
        environment.getLockReservationThreshold() != null
            ? environment.getLockReservationThreshold()
            : gitRepoSettingsService
                .getOrCreateGitRepoSettingsByRepositoryId(
                    environment.getRepository().getRepositoryId())
                .map(GitRepoSettingsDto::lockReservationThreshold)
                .orElse(-1L);
    if (environment.isLocked() && environment.getLockedAt() != null) {
      return lockReservationThreshold != -1
          ? environment.getLockedAt().plusMinutes(lockReservationThreshold)
          : null;
    } else {
      return null;
    }
  }

  /**
   * Marks the provided environment as having its status changed by setting the current timestamp.
   * It updates the environment's status change timestamp to the current instant and persists the
   * changes in the repository. This will cause the status check for the environment to run more
   * frequently for some time.
   *
   * @param environment the Environment instance whose status has been altered
   */
  public void markStatusAsChanged(Environment environment) {
    environment.setStatusChangedAt(Instant.now());
    environmentRepository.save(environment);
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

    // TODO User environment lockReservationExpiresAt instead of calcualting it as below
    Long lockReservationThreshold =
        environment.getLockReservationThreshold() != null
            ? environment.getLockReservationThreshold()
            : gitRepoSettingsService
                .getOrCreateGitRepoSettingsByRepositoryId(
                    environment.getRepository().getRepositoryId())
                .map(GitRepoSettingsDto::lockReservationThreshold)
                .orElse(-1L);

    OffsetDateTime now = OffsetDateTime.now();
    OffsetDateTime lockedAt = environment.getLockedAt();

    // Check if the current user can unlock the environment
    if (!currentUser.equals(environment.getLockedBy()) && !authService.isAtLeastMaintainer()) {
      // Allow unlocking if lockReservationThreshold is set and the lock is older than the
      // reservation threshold
      if (lockReservationThreshold == -1
          || lockedAt.plusMinutes(lockReservationThreshold).isAfter(now)) {
        throw new SecurityException(
            "You do not have permission to unlock this environment. Environment is locked by"
                + " another user and the reservation period has not elapsed.");
      }
    }

    // 20 minutes timeout for redeployment
    if (!canUnlock(environment, 20)) {
      throw new DeploymentException("Deployment is still in progress, please wait.");
    }

    environment.setLocked(false);
    environment.setLockedBy(null);
    environment.setLockedAt(null);
    environment.setLockWillExpireAt(null);
    environment.setLockReservationExpiresAt(null);

    Optional<EnvironmentLockHistory> openLock =
        lockHistoryRepository.findCurrentLockForEnabledEnvironment(environment.getId());
    if (openLock.isPresent()) {
      EnvironmentLockHistory openLockHistory = openLock.get();
      openLockHistory.setUnlockedAt(OffsetDateTime.now());
      openLockHistory.setUnlockedBy(currentUser);
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
                environment.setLockWillExpireAt(null);
                environment.setLockReservationExpiresAt(null);
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
              if (environmentDto.statusCheckType() != null) {
                environment.setStatusCheckType(environmentDto.statusCheckType());
                environment.setStatusUrl(environmentDto.statusUrl());
              } else {
                environment.setStatusCheckType(null);
                environment.setStatusUrl(null);
              }
              if (environmentDto.type() != null) {
                environment.setType(environmentDto.type());
              }
              environment.setLockExpirationThreshold(environmentDto.lockExpirationThreshold());
              environment.setLockReservationThreshold(environmentDto.lockReservationThreshold());
              if (environment.isLocked() && environment.getLockedAt() != null) {
                environment.setLockWillExpireAt(getLockWillExpireAt(environment));
                environment.setLockReservationExpiresAt(getLockReservationExpiresAt(environment));
              }

              environmentRepository.save(environment);
              return EnvironmentDto.fromEnvironment(environment);
            });
  }

  // Called by GitRepoSettingsService when lock expiration threshold is updated
  public void updateLockExpirationAndReservation(Long repositoryId) {
    List<Environment> lockedEnvironments =
        environmentRepository.findByRepositoryRepositoryIdAndLockedTrue(repositoryId);
    for (Environment environment : lockedEnvironments) {
      environment.setLockWillExpireAt(getLockWillExpireAt(environment));
      environment.setLockReservationExpiresAt(getLockReservationExpiresAt(environment));
      environmentRepository.save(environment);
    }
  }

  public EnvironmentLockHistoryDto getUsersCurrentLock() {
    final User currentUser = authService.getUserFromGithubId();
    Optional<EnvironmentLockHistory> lockHistory =
        lockHistoryRepository.findLatestLockForEnabledEnvironment(currentUser);

    return lockHistory
        .map(
            lock ->
                EnvironmentLockHistoryDto.fromEnvironmentLockHistory(
                    lock, this, releaseCandidateRepository))
        .orElse(null);
  }

  public List<EnvironmentLockHistoryDto> getLockHistoryByEnvironmentId(Long environmentId) {
    return lockHistoryRepository.findLockHistoriesByEnvironment(environmentId).stream()
        .map(
            lock ->
                EnvironmentLockHistoryDto.fromEnvironmentLockHistory(
                    lock, this, releaseCandidateRepository))
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
