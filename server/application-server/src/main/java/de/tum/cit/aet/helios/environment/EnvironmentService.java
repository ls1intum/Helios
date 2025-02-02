package de.tum.cit.aet.helios.environment;

import de.tum.cit.aet.helios.auth.AuthService;
import de.tum.cit.aet.helios.deployment.DeploymentException;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
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

  public EnvironmentService(EnvironmentRepository environmentRepository,
      EnvironmentLockHistoryRepository lockHistoryRepository,
      HeliosDeploymentRepository heliosDeploymentRepository,
      AuthService authService) {
    this.environmentRepository = environmentRepository;
    this.lockHistoryRepository = lockHistoryRepository;
    this.heliosDeploymentRepository = heliosDeploymentRepository;
    this.authService = authService;
  }

  public Optional<EnvironmentDto> getEnvironmentById(Long id) {
    return environmentRepository.findById(id).map(EnvironmentDto::fromEnvironment);
  }

  public List<EnvironmentDto> getAllEnvironments() {
    return environmentRepository.findAllByOrderByNameAsc().stream()
        .map(
            environment -> {
              return EnvironmentDto.fromEnvironment(
                  environment,
                  environment.getLatestDeployment(),
                  environment.getLatestStatus());
            })
        .collect(Collectors.toList());
  }

  public List<EnvironmentDto> getAllEnabledEnvironments() {
    return environmentRepository.findByEnabledTrueOrderByNameAsc().stream()
        .map(
            environment -> {
              return EnvironmentDto.fromEnvironment(
                  environment,
                  environment.getLatestDeployment(),
                  environment.getLatestStatus());
            })
        .collect(Collectors.toList());
  }

  public List<EnvironmentDto> getEnvironmentsByRepositoryId(Long repositoryId) {
    return environmentRepository.findByRepositoryRepositoryIdOrderByCreatedAtDesc(repositoryId)
        .stream()
        .map(EnvironmentDto::fromEnvironment)
        .collect(Collectors.toList());
  }

  /**
   * Locks the environment with the specified ID.
   *
   * <p>This method attempts to lock the environment by setting its locked status to
   * true. If the
   * environment is already locked, it returns an empty Optional. If the
   * environment is successfully
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
    final String currentUserName = authService.getPreferredUsername();

    Environment environment = environmentRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Environment not found with ID: " + id));

    if (!environment.isEnabled()) {
      throw new IllegalStateException("Environment is disabled");
    }

    if (environment.isLocked()) {
      if (currentUserName.equals(environment.getLockedBy())) {
        return Optional.of(environment);
      }

      return Optional.empty();
    }
    environment.setLockedBy(currentUserName);
    environment.setLockedAt(OffsetDateTime.now());
    environment.setLocked(true);

    // Record lock event
    EnvironmentLockHistory history = new EnvironmentLockHistory();
    history.setEnvironment(environment);
    history.setLockedBy(currentUserName);
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
    final String currentUserName = authService.getPreferredUsername();

    Environment environment = environmentRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Environment not found with ID: " + id));

    if (!environment.isLocked()) {
      throw new IllegalStateException("Environment is not locked");
    }

    if (!currentUserName.equals(environment.getLockedBy())) {
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

    Optional<EnvironmentLockHistory> openLock = lockHistoryRepository
        .findLatestLockForEnvironmentAndUser(environment, currentUserName);
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
   * <p>This method updates the environment with the specified ID using the provided
   * EnvironmentDto.
   *
   * @param id             the ID of the environment to update
   * @param environmentDto the EnvironmentDto containing the updated environment
   *                       information
   * @return an Optional containing the updated environment if successful,
   *         or an empty Optional if no environment is found with the specified ID
   * @throws EnvironmentException if the environment is locked and cannot be
   *                              disabled
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
              if (environmentDto.statusCheckType() != null) {
                environment.setStatusCheckType(environmentDto.statusCheckType());
                environment.setStatusUrl(environmentDto.statusUrl());
              } else {
                environment.setStatusCheckType(null);
                environment.setStatusUrl(null);
              }

              environmentRepository.save(environment);
              return EnvironmentDto.fromEnvironment(environment);
            });
  }

  public EnvironmentLockHistoryDto getUsersCurrentLock() {
    final String currentUserName = authService.getPreferredUsername();
    Optional<EnvironmentLockHistory> lockHistory = lockHistoryRepository
        .findLatestLockForEnabledEnvironment(currentUserName);

    return lockHistory.map(EnvironmentLockHistoryDto::fromEnvironmentLockHistory).orElse(null);
  }

  public List<EnvironmentLockHistoryDto> getLockHistoryByEnvironmentId(Long environmentId) {
    return lockHistoryRepository.findLockHistoriesByEnvironment(environmentId).stream()
        .map(EnvironmentLockHistoryDto::fromEnvironmentLockHistory)
        .collect(Collectors.toList());
  }

  private boolean canUnlock(Environment environment, long timeoutMinutes) {
    // Fetch the most recent deployment for the environment
    Optional<HeliosDeployment> latestDeployment = heliosDeploymentRepository
        .findTopByEnvironmentOrderByCreatedAtDesc(environment);

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
