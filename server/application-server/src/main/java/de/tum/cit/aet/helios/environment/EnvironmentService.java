package de.tum.cit.aet.helios.environment;

import de.tum.cit.aet.helios.auth.AuthService;
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

  private final EnvironmentRepository environmentRepository;

  private final AuthService authService;

  private final EnvironmentLockHistoryRepository lockHistoryRepository;


  public EnvironmentService(EnvironmentRepository environmentRepository,
                            EnvironmentLockHistoryRepository lockHistoryRepository,
                            AuthService authService) {
    this.environmentRepository = environmentRepository;
    this.lockHistoryRepository = lockHistoryRepository;
    this.authService = authService;
  }

  public Optional<EnvironmentDto> getEnvironmentById(Long id) {
    return environmentRepository.findById(id).map(EnvironmentDto::fromEnvironment);
  }

  public List<EnvironmentDto> getAllEnvironments() {
    return environmentRepository.findAll().stream()
        .map(
            environment -> {
              return EnvironmentDto.fromEnvironment(
                  environment, environment.getDeployments().reversed().stream().findFirst());
            })
        .collect(Collectors.toList());
  }

  public List<EnvironmentDto> getEnvironmentsByRepositoryId(Long repositoryId) {
    return environmentRepository.findByRepositoryIdOrderByCreatedAtDesc(repositoryId).stream()
        .map(EnvironmentDto::fromEnvironment)
        .collect(Collectors.toList());
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
    final String currentUserId = authService.getUserId();

    Environment environment =
        environmentRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Environment not found with ID: " + id));

    if (environment.isLocked()) {
      if (currentUserId.equals(environment.getLockedBy())) {
        return Optional.of(environment);
      }

      return Optional.empty();
    }
    environment.setLockedBy(currentUserId);
    environment.setLockedAt(OffsetDateTime.now());
    environment.setLocked(true);

    // Record lock event
    EnvironmentLockHistory history = new EnvironmentLockHistory();
    history.setEnvironment(environment);
    history.setLockedBy(currentUserId);
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
    final String currentUserId = authService.getUserId();

    Environment environment =
        environmentRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Environment not found with ID: " + id));

    if (!environment.isLocked()) {
      throw new IllegalStateException("Environment is not locked");
    }

    if (!currentUserId.equals(environment.getLockedBy())) {
      throw new SecurityException(
          "You do not have permission to unlock this environment. "
              + "Environment is locked by another user");
    }

    environment.setLocked(false);
    environment.setLockedBy(null);
    environment.setLockedAt(null);

    var openLock = lockHistoryRepository
        .findTopByEnvironmentAndLockedByAndUnlockedAtIsNullOrderByLockedAtDesc(
            environment,
            currentUserId
        );
    if (openLock != null) {
      openLock.setUnlockedAt(OffsetDateTime.now());
      lockHistoryRepository.save(openLock);
    }

    environmentRepository.save(environment);

    return EnvironmentDto.fromEnvironment(environment);
  }

  public Optional<EnvironmentDto> updateEnvironment(Long id, EnvironmentDto environmentDto) {
    return environmentRepository
        .findById(id)
        .map(
            environment -> {
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
    final String currentUserId = authService.getUserId();
    EnvironmentLockHistory lockHistory =
        lockHistoryRepository
            .findTopByLockedByAndUnlockedAtIsNullOrderByLockedAtDesc(currentUserId);

    if (lockHistory == null) {
      return null;
    }

    return EnvironmentLockHistoryDto.fromEnvironmentLockHistory(lockHistory);
  }
}
