package de.tum.cit.aet.helios.environment;

import de.tum.cit.aet.helios.config.security.annotations.EnforceAtLeastMaintainer;
import de.tum.cit.aet.helios.config.security.annotations.EnforceAtLeastWritePermission;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/environments")
public class EnvironmentController {

  private final EnvironmentService environmentService;

  @GetMapping
  public ResponseEntity<List<EnvironmentDto>> getAllEnvironments() {
    List<EnvironmentDto> environments = environmentService.getAllEnvironments();
    return ResponseEntity.ok(environments);
  }

  @GetMapping("/enabled")
  public ResponseEntity<List<EnvironmentDto>> getAllEnabledEnvironments() {
    List<EnvironmentDto> environments = environmentService.getAllEnabledEnvironments();
    return ResponseEntity.ok(environments);
  }

  @GetMapping("/{id}")
  public ResponseEntity<EnvironmentDto> getEnvironmentById(@PathVariable Long id) {
    Optional<EnvironmentDto> environment = environmentService.getEnvironmentById(id);
    return environment.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/repository/{repositoryId}")
  public ResponseEntity<List<EnvironmentDto>> getEnvironmentsByRepositoryId(
      @PathVariable Long repositoryId) {
    List<EnvironmentDto> environments =
        environmentService.getEnvironmentsByRepositoryId(repositoryId);
    return ResponseEntity.ok(environments);
  }

  @GetMapping("/userLocking")
  public ResponseEntity<EnvironmentLockHistoryDto> getEnvironmentsByUserLocking() {
    EnvironmentLockHistoryDto usersLock = environmentService.getUsersCurrentLock();
    return ResponseEntity.ok(usersLock);
  }

  @GetMapping("/environment/{environmentId}/lockHistory")
  public ResponseEntity<List<EnvironmentLockHistoryDto>> getLockHistoryByEnvironmentId(
      @PathVariable Long environmentId) {
    List<EnvironmentLockHistoryDto> lockHistory =
        environmentService.getLockHistoryByEnvironmentId(environmentId);

    return ResponseEntity.ok(lockHistory);
  }

  @EnforceAtLeastWritePermission
  @PutMapping("/{id}/unlock")
  public ResponseEntity<?> unlockEnvironment(@PathVariable Long id) {
    EnvironmentDto environment = environmentService.unlockEnvironment(id);
    return ResponseEntity.ok(environment);
  }

  @EnforceAtLeastWritePermission
  @PutMapping("/{id}/lock")
  public ResponseEntity<?> lockEnvironment(@PathVariable Long id) {
    Optional<Environment> environment = environmentService.lockEnvironment(id);
    return ResponseEntity.ok(EnvironmentDto.fromEnvironment(environment.get()));
  }

  @EnforceAtLeastWritePermission
  @PutMapping("/{id}/extend-lock")
  public ResponseEntity<?> extendEnvironmentLock(@PathVariable Long id) {
    Optional<Environment> environment = environmentService.extendEnvironmentLock(id);
    return environment
        .map(env -> ResponseEntity.ok(EnvironmentDto.fromEnvironment(env)))
        .orElse(ResponseEntity.notFound().build());
  }

  @EnforceAtLeastMaintainer
  @PutMapping("/{id}")
  public ResponseEntity<?> updateEnvironment(
      @PathVariable Long id, @RequestBody EnvironmentDto environmentDto) {
    Optional<EnvironmentDto> updatedEnvironment =
        environmentService.updateEnvironment(id, environmentDto);
    return updatedEnvironment.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/{environmentId}/reviewers")
  public ResponseEntity<EnvironmentReviewersDto> getEnvironmentReviewers(
      @PathVariable Long environmentId) {
    return environmentService
        .getEnvironmentReviewers(environmentId)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @PostMapping("/sync")
  public ResponseEntity<?> syncEnvironments() {
    try {
      environmentService.syncRepositoryEnvironments();
    } catch (IOException e) {
      return ResponseEntity.status(500).body("Error syncing environments: " + e.getMessage());
    }
    return ResponseEntity.ok().build();
  }
}
