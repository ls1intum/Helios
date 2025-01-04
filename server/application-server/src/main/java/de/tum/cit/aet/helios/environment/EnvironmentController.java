package de.tum.cit.aet.helios.environment;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/environments")
public class EnvironmentController {

  private final EnvironmentService environmentService;

  public EnvironmentController(EnvironmentService environmentService) {
    this.environmentService = environmentService;
  }

  @GetMapping
  public ResponseEntity<List<EnvironmentDto>> getAllEnvironments() {
    List<EnvironmentDto> environments = environmentService.getAllEnvironments();
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

  @GetMapping("/user_locking")
  public ResponseEntity<EnvironmentLockHistoryDto> getEnvironmentsByUserLocking() {
    EnvironmentLockHistoryDto usersLock = environmentService.getUsersCurrentLock();
    return ResponseEntity.ok(usersLock);
  }

  @PutMapping("/{id}/unlock")
  public ResponseEntity<?> unlockEnvironment(@PathVariable Long id) {
    try {
      EnvironmentDto environment = environmentService.unlockEnvironment(id);
      return ResponseEntity.ok(environment);

    } catch (EntityNotFoundException e) {
      // 404 Not Found
      return ResponseEntity
          .status(HttpStatus.NOT_FOUND)
          .body("Unlock Failed: " + e.getMessage());

    } catch (IllegalStateException e) {
      // 400 Bad Request
      return ResponseEntity
          .status(HttpStatus.BAD_REQUEST)
          .body("Unlock Failed: " + e.getMessage());

    } catch (SecurityException e) {
      // 403 Forbidden
      return ResponseEntity
          .status(HttpStatus.FORBIDDEN)
          .body("Unlock Failed: " + e.getMessage());

    } catch (Exception e) {
      // 500 Internal Server Error (fallback for any other exception)
      return ResponseEntity
          .status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("Unlock Failed: " + e.getMessage());
    }
  }

  @PutMapping("/{id}")
  public ResponseEntity<EnvironmentDto> updateEnvironment(
      @PathVariable Long id, @RequestBody EnvironmentDto environmentDto) {
    Optional<EnvironmentDto> updatedEnvironment =
        environmentService.updateEnvironment(id, environmentDto);
    return updatedEnvironment.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
  }
}
