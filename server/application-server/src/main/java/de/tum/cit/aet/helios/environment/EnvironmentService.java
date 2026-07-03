package de.tum.cit.aet.helios.environment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.helios.auth.AuthService;
import de.tum.cit.aet.helios.deployment.Deployment;
import de.tum.cit.aet.helios.deployment.DeploymentException;
import de.tum.cit.aet.helios.deployment.DeploymentRepository;
import de.tum.cit.aet.helios.deployment.LatestDeploymentUnion;
import de.tum.cit.aet.helios.environment.github.GitHubEnvironmentDto;
import de.tum.cit.aet.helios.environment.github.GitHubEnvironmentProtectionRuleDto;
import de.tum.cit.aet.helios.environment.github.GitHubEnvironmentSyncService;
import de.tum.cit.aet.helios.environment.protectionrules.ProtectionRule;
import de.tum.cit.aet.helios.environment.protectionrules.ProtectionRuleRepository;
import de.tum.cit.aet.helios.filters.RepositoryContext;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.gitreposettings.GitRepoSettingsDto;
import de.tum.cit.aet.helios.gitreposettings.GitRepoSettingsService;
import de.tum.cit.aet.helios.heliosdeployment.DeploymentDurationEstimate;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeploymentRepository;
import de.tum.cit.aet.helios.nats.NatsNotificationPublisherService;
import de.tum.cit.aet.helios.notification.email.LockReleasedPayload;
import de.tum.cit.aet.helios.releaseinfo.releasecandidate.ReleaseCandidateRepository;
import de.tum.cit.aet.helios.user.User;
import de.tum.cit.aet.helios.workflow.Workflow;
import de.tum.cit.aet.helios.workflow.WorkflowRepository;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.kohsuke.github.GHRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@Log4j2
@RequiredArgsConstructor
public class EnvironmentService {

  private static final long ACTIVE_DEPLOYMENT_UNLOCK_TIMEOUT_MINUTES = 20;
  private static final String ACTIVE_DEPLOYMENT_UNLOCK_ERROR_MESSAGE =
      "Cancel the ongoing deployment before unlocking this environment.";

  private final AuthService authService;
  private final EnvironmentRepository environmentRepository;
  private final EnvironmentLockHistoryRepository lockHistoryRepository;
  private final HeliosDeploymentRepository heliosDeploymentRepository;
  private final ReleaseCandidateRepository releaseCandidateRepository;
  private final DeploymentRepository deploymentRepository;
  @Lazy private final GitRepoSettingsService gitRepoSettingsService;
  private final WorkflowRepository workflowRepository;
  private final WorkflowRunRepository workflowRunRepository;
  private final ProtectionRuleRepository protectionRuleRepository;
  private final ObjectMapper objectMapper;
  private final GitHubEnvironmentSyncService environmentSyncService;
  private final GitRepoRepository gitRepoRepository;
  private final GitHubService gitHubService;
  private final NatsNotificationPublisherService notificationPublisherService;

  public Optional<EnvironmentDto> getEnvironmentById(Long id) {
    return findScopedById(id).map(EnvironmentDto::fromEnvironment);
  }

  public Optional<Environment.Type> getEnvironmentTypeById(Long id) {
    return findScopedById(id).map(Environment::getType);
  }

  /**
   * Loads an environment by id, scoped to the current repository when a repository context is
   * present. Explicit replacement for the ambient gitRepositoryFilter, which never applied to
   * findById/PK loads — so this also closes a latent cross-repository read.
   */
  private Optional<Environment> findScopedById(Long id) {
    Long repositoryId = RepositoryContext.getRepositoryId();
    return repositoryId == null
        ? environmentRepository.findById(id)
        : environmentRepository.findByIdAndRepositoryRepositoryId(id, repositoryId);
  }

  public List<EnvironmentDto> getAllEnvironments() {
    Long repositoryId = RepositoryContext.getRepositoryId();
    if (repositoryId == null) {
      return List.of();
    }
    return environmentRepository.findByRepositoryRepositoryIdOrderByNameAsc(repositoryId).stream()
        .map(
            environment -> {
              LatestDeploymentUnion latest = findLatestDeployment(environment);
              DeploymentDurationEstimate estimate = computeEstimate(environment);
              return EnvironmentDto.fromEnvironment(
                  environment, latest, environment.getLatestStatus(), releaseCandidateRepository,
                  estimate);
            })
        .collect(Collectors.toList());
  }

  public List<EnvironmentDto> getAllEnabledEnvironments() {
    Long repositoryId = RepositoryContext.getRepositoryId();
    if (repositoryId == null) {
      return List.of();
    }
    return environmentRepository
        .findByEnabledTrueAndRepositoryRepositoryIdOrderByNameAsc(repositoryId)
        .stream()
        .map(
            environment -> {
              LatestDeploymentUnion latest = findLatestDeployment(environment);
              DeploymentDurationEstimate estimate = computeEstimate(environment);
              return EnvironmentDto.fromEnvironment(
                  environment, latest, environment.getLatestStatus(), releaseCandidateRepository,
                  estimate);
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
   * Computes median pre-deploy and deploy duration estimates from previous deployments for the
   * given environment.
   *
   * @param environment the environment whose duration estimates should be loaded
   * @return a duration estimate, or {@code null} when no usable historical data exists
   */
  private DeploymentDurationEstimate computeEstimate(Environment environment) {
    List<Object[]> rows =
        heliosDeploymentRepository.findMedianDurationsByEnvironmentId(environment.getId());
    if (rows == null || rows.isEmpty()) {
      return null;
    }
    Object[] row = rows.get(0);
    if (row == null || row.length < 2 || row[0] == null) {
      return null;
    }
    Double medianPreDeploy = ((Number) row[0]).doubleValue();
    Double medianDeploy = row[1] != null ? ((Number) row[1]).doubleValue() : null;
    return new DeploymentDurationEstimate(medianPreDeploy, medianDeploy);
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
      throw new EnvironmentException("Environment is disabled");
    }

    // Only proceed with locking if it's a TEST environment
    if (environment.getType() != Environment.Type.TEST) {
      // Return the environment without locking for non-TEST environments
      throw new EnvironmentException("Only test environments can be locked");
    }

    if (environment.isLocked()) {
      if (currentUser.equals(environment.getLockedBy())) {
        // Already locked by current user, return the environment
        return Optional.of(environment);
      }
      // Locked by some other user
      String msg = getLockedByAnotherUserErrorMessage(environment);
      throw new EnvironmentException(msg);
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

  /**
   * Calculates lock expiration time based on a base time and threshold settings.
   *
   * @param environment the environment to calculate lock expiration for
   * @param baseTime the base time from which to calculate expiration
   * @return the calculated expiration time or null if no threshold is set
   */
  @Transactional
  protected OffsetDateTime calculateLockExpiration(
      Environment environment, OffsetDateTime baseTime) {
    Long lockExpirationThreshold =
        environment.getLockExpirationThreshold() != null
            ? environment.getLockExpirationThreshold()
            : gitRepoSettingsService
            .getOrCreateGitRepoSettingsByRepositoryId(
                environment.getRepository().getRepositoryId())
            .map(GitRepoSettingsDto::lockExpirationThreshold)
            .orElse(-1L);

    return lockExpirationThreshold != -1 ? baseTime.plusMinutes(lockExpirationThreshold) : null;
  }

  /**
   * Calculates reservation expiration time based on a base time and threshold settings.
   *
   * @param environment the environment to calculate reservation expiration for
   * @param baseTime the base time from which to calculate expiration
   * @return the calculated expiration time or null if no threshold is set
   */
  @Transactional
  protected OffsetDateTime calculateReservationExpiration(
      Environment environment, OffsetDateTime baseTime) {
    Long lockReservationThreshold =
        environment.getLockReservationThreshold() != null
            ? environment.getLockReservationThreshold()
            : gitRepoSettingsService
            .getOrCreateGitRepoSettingsByRepositoryId(
                environment.getRepository().getRepositoryId())
            .map(GitRepoSettingsDto::lockReservationThreshold)
            .orElse(-1L);

    return lockReservationThreshold != -1 ? baseTime.plusMinutes(lockReservationThreshold) : null;
  }

  @Transactional
  protected OffsetDateTime getLockWillExpireAt(Environment environment) {
    if (environment.isLocked() && environment.getLockedAt() != null) {
      return calculateLockExpiration(environment, environment.getLockedAt());
    } else {
      return null;
    }
  }

  @Transactional
  protected OffsetDateTime getLockReservationExpiresAt(Environment environment) {
    if (environment.isLocked() && environment.getLockedAt() != null) {
      return calculateReservationExpiration(environment, environment.getLockedAt());
    } else {
      return null;
    }
  }

  /**
   * Extends the lock duration for an already locked environment.
   *
   * <p>This method attempts to extend the lock duration by resetting the lockWillExpireAt and
   * lockReservationExpiresAt timestamps based on the current time. The environment must already be
   * locked by the current user.
   *
   * <p>This method is transactional and handles optimistic locking failures.
   *
   * @param id the ID of the environment to extend the lock for
   * @return an Optional containing the updated environment if successful, or an empty Optional if
   *     the environment isn't locked, is locked by another user, or if an optimistic locking
   *     failure occurs
   * @throws EntityNotFoundException if no environment is found with the specified ID
   * @throws EnvironmentException if the environment is disabled or isn't a TEST environment
   */
  @Transactional
  public Optional<Environment> extendEnvironmentLock(Long id) {
    final User currentUser = authService.getUserFromGithubId();

    Environment environment =
        environmentRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Environment not found with ID: " + id));

    // Check environment status
    if (!environment.isEnabled()) {
      throw new EnvironmentException("Environment is disabled");
    }

    if (environment.getType() != Environment.Type.TEST) {
      throw new EnvironmentException("Only TEST environments can have their locks extended");
    }

    // Verify the environment is locked and by the current user
    if (!environment.isLocked()) {
      throw new EnvironmentException("Environment is not locked. Cannot extend lock.");
    }

    if (!environment.getLockedBy().equals(currentUser)) {
      // The Environment is locked, but by someone else
      String msg = getLockedByAnotherUserErrorMessage(environment);
      throw new EnvironmentException(msg);
    }

    // Calculate new expiration times based on current time
    environment.setLockWillExpireAt(calculateLockExpiration(environment, OffsetDateTime.now()));
    environment.setLockReservationExpiresAt(
        calculateReservationExpiration(environment, OffsetDateTime.now()));

    try {
      environmentRepository.save(environment);
    } catch (OptimisticLockingFailureException e) {
      // The environment was modified by another transaction
      return Optional.empty();
    }

    return Optional.of(environment);
  }

  @NotNull
  private static String getLockedByAnotherUserErrorMessage(Environment environment) {
    final StringBuilder msg = new StringBuilder("Environment is locked by another user.\n");
    final ZoneId localZone = ZoneId.systemDefault();
    final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    if (environment.getLockedBy() != null) {
      msg.append(" (User: ").append(environment.getLockedBy().getLogin()).append(")\n");
    }
    if (environment.getLockedAt() != null) {
      final OffsetDateTime lockedAtUtc = environment.getLockedAt(); // assume stored in UTC
      final OffsetDateTime lockedAtLocal =
          lockedAtUtc.atZoneSameInstant(localZone).toOffsetDateTime();
      final String lockedAtString = dateFormatter.format(lockedAtLocal);
      msg.append(" (Locked at: ").append(lockedAtString).append(")\n");
    }
    if (environment.getLockWillExpireAt() != null) {
      final OffsetDateTime expireAtUtc = environment.getLockWillExpireAt();
      final OffsetDateTime expireAtLocal =
          expireAtUtc.atZoneSameInstant(localZone).toOffsetDateTime();
      final String expireAtString = dateFormatter.format(expireAtLocal);
      msg.append(" (Lock will expire at: ").append(expireAtString).append(")");
    }
    return msg.toString();
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
      throw new EnvironmentException("Environment is not locked");
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

    validateNoBlockingActiveDeployment(environment);

    // Publish email notification for lock unlocked
    // Publish only if the unlocker is different from the locker
    if (environment.getLockedBy() != null
        && !environment.getLockedBy().getId().equals(currentUser.getId())) {
      notificationPublisherService.send(
          environment.getLockedBy(),
          new LockReleasedPayload(
              environment.getLockedBy().getLogin(),
              currentUser.getLogin(),
              environment.getName(),
              environment.getRepository().getRepositoryId().toString(),
              environment.getRepository().getNameWithOwner()
          )
      );
    } else {
      log.info(
          "No email notification sent for lock release, as the locker and unlocker are the same.");
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
  // Atomic: many field mutations plus lock-expiry recalculation on one environment.
  @Transactional
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

              if (environmentDto.deploymentWorkflowBranch() != null
                  && !environmentDto.deploymentWorkflowBranch().isEmpty()) {
                try {
                  var ghRepository =
                      this.gitHubService.getRepository(
                          environment.getRepository().getNameWithOwner());

                  // This will throw an exception if the branch does not exist
                  ghRepository.getBranch(environmentDto.deploymentWorkflowBranch());
                } catch (Exception e) {
                  throw new EnvironmentException(
                      "The selected deployment workflow branch does not exist in the GitHub"
                          + " repository. Please select a valid branch or leave it empty.");
                }
              }

              environment.setDeploymentWorkflowBranch(environmentDto.deploymentWorkflowBranch());

              if (environmentDto.updatedAt() != null) {
                environment.setUpdatedAt(environmentDto.updatedAt());
              }
              if (environmentDto.installedApps() != null) {
                environment.setInstalledApps(environmentDto.installedApps());
              }
              if (environmentDto.description() != null) {
                environment.setDescription(environmentDto.description());
              }
              if (environmentDto.displayName() != null) {
                environment.setDisplayName(environmentDto.displayName());
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
              if (environmentDto.deploymentWorkflow() != null) {
                Long workflowId = environmentDto.deploymentWorkflow().id();
                Workflow wf =
                    workflowRepository
                        .findById(workflowId)
                        .orElseThrow(
                            () ->
                                new EntityNotFoundException(
                                    "Workflow not found with ID: " + workflowId));
                environment.setDeploymentWorkflow(wf);
              }
              updateRequiredPreDeploymentWorkflows(environment, environmentDto);
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

  private void updateRequiredPreDeploymentWorkflows(
      Environment environment, EnvironmentDto environmentDto) {
    if (environmentDto.requiredPreDeploymentWorkflows() == null) {
      environment.setRequiredPreDeploymentWorkflows(new ArrayList<>());
      return;
    }

    List<Workflow> workflows = environmentDto.requiredPreDeploymentWorkflows().stream()
        .map(workflowDto -> workflowRepository
            .findById(workflowDto.id())
            .orElseThrow(() -> new EntityNotFoundException(
                "Workflow not found with ID: " + workflowDto.id())))
        .toList();

    boolean containsCrossRepositoryWorkflow = workflows.stream()
        .anyMatch(workflow -> workflow.getRepository() == null
            || environment.getRepository() == null
            || !workflow.getRepository().getRepositoryId()
            .equals(environment.getRepository().getRepositoryId()));

    if (containsCrossRepositoryWorkflow) {
      throw new EnvironmentException(
          "Required pre-deployment workflows must belong to the same repository as the"
              + " environment.");
    }

    Workflow deploymentWorkflow = environment.getDeploymentWorkflow();
    if (deploymentWorkflow != null) {
      Set<Long> requiredWorkflowIds =
          workflows.stream().map(Workflow::getId).collect(Collectors.toSet());
      if (requiredWorkflowIds.contains(deploymentWorkflow.getId())) {
        throw new EnvironmentException(
            "The deployment workflow cannot also be configured as a required pre-deployment"
                + " workflow.");
      }
    }

    environment.setRequiredPreDeploymentWorkflows(new ArrayList<>(workflows));
  }

  public EnvironmentDeploymentReadinessDto getDeploymentReadiness(
      Long environmentId, String branch, String sha) {
    Environment environment =
        environmentRepository
            .findById(environmentId)
            .orElseThrow(
                () ->
                    new EntityNotFoundException("Environment not found with ID: " + environmentId));

    List<Workflow> requiredWorkflows = environment.getRequiredPreDeploymentWorkflows();
    if (requiredWorkflows == null || requiredWorkflows.isEmpty()) {
      return new EnvironmentDeploymentReadinessDto(
          EnvironmentDeploymentReadinessDto.Status.UNCONFIGURED,
          List.of());
    }

    List<EnvironmentDeploymentReadinessDto.RequiredWorkflowStatusDto> workflowStatuses =
        requiredWorkflows.stream()
            .sorted((left, right) -> left.getName().compareToIgnoreCase(right.getName()))
            .map(workflow -> buildRequiredWorkflowStatus(environment, workflow, branch, sha))
            .toList();

    boolean hasFailed =
        workflowStatuses.stream()
            .anyMatch(
                status ->
                    status.status() == EnvironmentDeploymentReadinessDto.WorkflowStatus.FAILED);
    boolean hasWaiting =
        workflowStatuses.stream()
            .anyMatch(
                status ->
                    status.status() == EnvironmentDeploymentReadinessDto.WorkflowStatus.WAITING);
    boolean hasMissingRun =
        workflowStatuses.stream()
            .anyMatch(
                status ->
                    status.status()
                        == EnvironmentDeploymentReadinessDto.WorkflowStatus.MISSING_RUN);

    EnvironmentDeploymentReadinessDto.Status status =
        hasFailed
            ? EnvironmentDeploymentReadinessDto.Status.FAILED
            : hasWaiting
                ? EnvironmentDeploymentReadinessDto.Status.WAITING
                : hasMissingRun
                    ? EnvironmentDeploymentReadinessDto.Status.MISSING_RUN
                    : EnvironmentDeploymentReadinessDto.Status.READY;

    return new EnvironmentDeploymentReadinessDto(status, workflowStatuses);
  }

  private EnvironmentDeploymentReadinessDto.RequiredWorkflowStatusDto buildRequiredWorkflowStatus(
      Environment environment, Workflow workflow, String branch, String sha) {
    Optional<WorkflowRun> latestRun =
        workflowRunRepository
            .findLatestForDeploymentReadiness(
                workflow.getId(),
                environment.getRepository().getRepositoryId(),
                branch,
                sha,
                PageRequest.of(0, 1))
            .stream()
            .findFirst();

    if (latestRun.isEmpty()) {
      return new EnvironmentDeploymentReadinessDto.RequiredWorkflowStatusDto(
          workflow.getId(),
          workflow.getName(),
          EnvironmentDeploymentReadinessDto.WorkflowStatus.MISSING_RUN,
          null,
          null,
          null,
          null);
    }

    WorkflowRun run = latestRun.get();
    return new EnvironmentDeploymentReadinessDto.RequiredWorkflowStatusDto(
        workflow.getId(),
        workflow.getName(),
        mapWorkflowRunToReadinessStatus(run),
        run.getId(),
        run.getHtmlUrl(),
        run.getStatus(),
        run.getConclusion().orElse(null));
  }

  private EnvironmentDeploymentReadinessDto.WorkflowStatus mapWorkflowRunToReadinessStatus(
      WorkflowRun run) {
    return switch (run.getStatus()) {
      case COMPLETED -> run.getConclusion().orElse(WorkflowRun.Conclusion.UNKNOWN)
              == WorkflowRun.Conclusion.SUCCESS
          ? EnvironmentDeploymentReadinessDto.WorkflowStatus.READY
          : EnvironmentDeploymentReadinessDto.WorkflowStatus.FAILED;
      case REQUESTED, QUEUED, PENDING, WAITING, IN_PROGRESS, ACTION_REQUIRED ->
          EnvironmentDeploymentReadinessDto.WorkflowStatus.WAITING;
      default -> EnvironmentDeploymentReadinessDto.WorkflowStatus.FAILED;
    };
  }

  // Called by GitRepoSettingsService when lock expiration threshold is updated
  // Atomic: recompute and persist expiry for every locked environment together.
  @Transactional
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

  private void validateNoBlockingActiveDeployment(Environment environment) {
    Optional<HeliosDeployment> latestDeployment =
        heliosDeploymentRepository.findTopByEnvironmentOrderByCreatedAtDesc(environment);

    if (latestDeployment.isEmpty()) {
      return;
    }

    HeliosDeployment deployment = latestDeployment.get();

    if (!isActiveDeploymentStatus(deployment.getStatus())) {
      return;
    }

    OffsetDateTime statusUpdatedAt = deployment.getStatusUpdatedAt();
    boolean deploymentIsStale =
        statusUpdatedAt != null
            && !statusUpdatedAt
                .plusMinutes(ACTIVE_DEPLOYMENT_UNLOCK_TIMEOUT_MINUTES)
                .isAfter(OffsetDateTime.now());
    if (!deploymentIsStale) {
      throw new DeploymentException(ACTIVE_DEPLOYMENT_UNLOCK_ERROR_MESSAGE);
    }

    deployment.setStatus(HeliosDeployment.Status.UNKNOWN);
    heliosDeploymentRepository.save(deployment);
  }

  private boolean isActiveDeploymentStatus(HeliosDeployment.Status status) {
    return status == HeliosDeployment.Status.WAITING
        || status == HeliosDeployment.Status.QUEUED
        || status == HeliosDeployment.Status.IN_PROGRESS;
  }

  public Optional<EnvironmentReviewersDto> getEnvironmentReviewers(Long environmentId) {
    return protectionRuleRepository
        .findByEnvironmentIdAndRuleType(environmentId, ProtectionRule.RuleType.REQUIRED_REVIEWERS)
        .map(
            rule -> {
              try {
                // Parse the stored JSON string as a list of ReviewerContainer objects
                List<GitHubEnvironmentProtectionRuleDto.ReviewerContainer>
                    githubReviewerContainers =
                    objectMapper.readValue(
                        rule.getReviewers(),
                        objectMapper
                            .getTypeFactory()
                            .constructCollectionType(
                                List.class,
                                GitHubEnvironmentProtectionRuleDto.ReviewerContainer.class));

                // Map to the simplified DTO structure
                List<EnvironmentReviewersDto.Reviewer> reviewers =
                    githubReviewerContainers.stream()
                        .map(
                            container -> {
                              GitHubEnvironmentProtectionRuleDto.Reviewer ghReviewer =
                                  container.getReviewer();

                              // Create the appropriate DTO Reviewer based on the container type
                              if ("User".equals(container.getType())) {
                                return new EnvironmentReviewersDto.Reviewer(
                                    ghReviewer.getId(), ghReviewer.getLogin());
                              } else { // Team
                                return new EnvironmentReviewersDto.Reviewer(
                                    ghReviewer.getId(), ghReviewer.getName(), true);
                              }
                            })
                        .toList();
                Boolean preventSelfReview = Boolean.TRUE.equals(rule.getPreventSelfReview());
                return new EnvironmentReviewersDto(preventSelfReview, reviewers);
              } catch (JsonProcessingException e) {
                log.error(
                    "Failed to parse reviewers for environment {}: {}",
                    environmentId,
                    e.getMessage());
                return null;
              }
            });
  }

  /**
   * Leaner sibling of {@link #getEnvironmentReviewers(Long)} for hot-path approval checks. Avoids
   * constructing the public-facing DTO; returns the GitHub logins / team slugs directly along with
   * whether the rule even exists and whether self-review is prevented.
   *
   * <p>An {@link Optional#empty()} result means "no {@code REQUIRED_REVIEWERS} rule on this env" —
   * the deployment is not gated on reviewers (it may still be gated on wait-timer or branch
   * policy, which GitHub handles itself).
   */
  public Optional<ReviewerResolution> resolveReviewers(Long environmentId) {
    return protectionRuleRepository
        .findByEnvironmentIdAndRuleType(environmentId, ProtectionRule.RuleType.REQUIRED_REVIEWERS)
        .map(
            rule -> {
              Set<String> userLogins = new java.util.HashSet<>();
              Set<String> teamSlugs = new java.util.HashSet<>();
              try {
                List<GitHubEnvironmentProtectionRuleDto.ReviewerContainer> containers =
                    objectMapper.readValue(
                        rule.getReviewers(),
                        objectMapper
                            .getTypeFactory()
                            .constructCollectionType(
                                List.class,
                                GitHubEnvironmentProtectionRuleDto.ReviewerContainer.class));
                for (GitHubEnvironmentProtectionRuleDto.ReviewerContainer c : containers) {
                  GitHubEnvironmentProtectionRuleDto.Reviewer r = c.getReviewer();
                  if ("User".equals(c.getType()) && r != null && r.getLogin() != null) {
                    userLogins.add(r.getLogin());
                  } else if ("Team".equals(c.getType()) && r != null && r.getName() != null) {
                    teamSlugs.add(r.getName());
                  }
                }
              } catch (JsonProcessingException e) {
                log.error(
                    "Failed to parse reviewers for environment {}: {}. Treating the rule as "
                        + "unresolved (deployment left for manual review) rather than deferring to "
                        + "zero reviewers, which would have stamped DEFERRED and notified nobody.",
                    environmentId,
                    e.getMessage());
                return null; // -> Optional.empty(): the caller treats this as "no actionable rule".
              }
              // A null prevent_self_review from GitHub collapses to false (self-review allowed),
              // matching GitHub's own default; auto-approval still only fires for an explicitly
              // configured User-type reviewer, so this is the safe default.
              return new ReviewerResolution(
                  Boolean.TRUE.equals(rule.getPreventSelfReview()), userLogins, teamSlugs);
            });
  }

  /**
   * Convenience predicate: is the user one of the User-type required reviewers for this env?
   * Returns {@code false} if there's no rule, or only Team-type reviewers (which Helios doesn't yet
   * expand to members).
   */
  public boolean isUserRequiredReviewer(Long environmentId, String userLogin) {
    if (userLogin == null) {
      return false;
    }
    return resolveReviewers(environmentId)
        .map(r -> r.userLogins().contains(userLogin))
        .orElse(false);
  }

  /**
   * Lean reviewer info for the approval flow. {@code userLogins} are GitHub User-type required
   * reviewers; {@code teamSlugs} are Team-type reviewers (left unexpanded for now — Helios falls
   * back to today's "impersonate creator" behaviour when teams are present).
   */
  public record ReviewerResolution(
      boolean preventSelfReview, Set<String> userLogins, Set<String> teamSlugs) {
    public boolean hasTeamReviewers() {
      return !teamSlugs.isEmpty();
    }
  }

  /**
   * Synchronizes the environments of the current repository with the GitHub repository.
   */
  // Atomic: reconciles (creates/updates/deletes) the repository's environments as one unit.
  @Transactional
  public void syncRepositoryEnvironments() throws IOException {
    Long repoId = RepositoryContext.getRepositoryId();
    GitRepository repo = gitRepoRepository.findById(repoId).orElseThrow();
    GHRepository ghRepository;
    try {
      ghRepository = this.gitHubService.getRepository(repo.getNameWithOwner());
    } catch (IOException e) {
      log.error("Failed to get GitHub repository: {}", e.getMessage());
      throw new EntityNotFoundException("GitHub repository not found for repository ID: " + repoId);
    }
    if (ghRepository == null) {
      log.error("GitHub repository not found for repository ID: {}", repoId);
      throw new EntityNotFoundException("GitHub repository not found for repository ID: " + repoId);
    }
    try {
      GitHubService.EnvironmentFetchResult environmentFetchResult =
          gitHubService.fetchEnvironments(ghRepository);
      List<GitHubEnvironmentDto> gitHubEnvironmentDtos = environmentFetchResult.environments();

      for (GitHubEnvironmentDto gitHubEnvironmentDto : gitHubEnvironmentDtos) {
        environmentSyncService.processEnvironment(gitHubEnvironmentDto, ghRepository);
      }

      if (environmentFetchResult.complete()) {
        environmentSyncService.removeDeletedEnvironments(gitHubEnvironmentDtos, repoId);
      } else {
        log.warn(
            "Skipping deleted environment cleanup for repository {} because the GitHub"
                + " environment list was incomplete.",
            ghRepository.getFullName());
      }

    } catch (IOException e) {
      log.error(
          "Failed to sync environments for repository {}: {}",
          ghRepository.getFullName(),
          e.getMessage());
      throw new IOException(
          "Failed to sync environments for repository "
              + ghRepository.getFullName()
              + ": "
              + e.getMessage());
    }
  }
}
