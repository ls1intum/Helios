package de.tum.cit.aet.helios.environment;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

@Service
@Transactional
public class EnvironmentService {

  private final EnvironmentRepository environmentRepository;

  public EnvironmentService(EnvironmentRepository environmentRepository) {
    this.environmentRepository = environmentRepository;
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
    Environment environment =
        environmentRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Environment not found with ID: " + id));

    var authentication = SecurityContextHolder.getContext().getAuthentication();
    var principal = (Jwt) authentication.getPrincipal();
    var currentUserId = principal.getSubject();
    if (environment.isLocked()) {
      return Optional.empty();
    }

    environment.setLocked(true);
    environment.setLockedBy(currentUserId);

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
    Environment environment =
        environmentRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Environment not found with ID: " + id));

    var authentication = SecurityContextHolder.getContext().getAuthentication();
    var principal = (Jwt) authentication.getPrincipal();
    var currentUserId = principal.getSubject();
    if (!environment.isLocked() || !currentUserId.equals(environment.getLockedBy())) {
      // Handle unauthorized unlock attempt return error
      return EnvironmentDto.fromEnvironment(environment);
    }

    environment.setLocked(false);
    environment.setLockedBy(null);
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
}
