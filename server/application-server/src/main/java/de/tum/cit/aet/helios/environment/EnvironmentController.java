package de.tum.cit.aet.helios.environment;

import java.util.List;
import java.util.Optional;
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

  @PutMapping("/{id}/unlock")
  public ResponseEntity<EnvironmentDto> unlockEnvironment(@PathVariable Long id) {
    // TODO: Check whether the user has the necessary permissions to unlock the environment
    EnvironmentDto environment = environmentService.unlockEnvironment(id);
    return ResponseEntity.ok(environment);
  }

  @PutMapping("/{id}")
  public ResponseEntity<EnvironmentDto> updateEnvironment(
      @PathVariable Long id, @RequestBody EnvironmentDto environmentDto) {
    Optional<EnvironmentDto> updatedEnvironment =
        environmentService.updateEnvironment(id, environmentDto);
    return updatedEnvironment.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
  }
}
