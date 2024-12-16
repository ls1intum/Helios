package de.tum.cit.aet.helios.environment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/environments")
public class EnvironmentController {

    private final EnvironmentService environmentService;

    public EnvironmentController(EnvironmentService environmentService) {
        this.environmentService = environmentService;
    }

    @GetMapping
    public ResponseEntity<List<EnvironmentDTO>> getAllEnvironments() {
        List<EnvironmentDTO> environments = environmentService.getAllEnvironments();
        return ResponseEntity.ok(environments);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EnvironmentDTO> getEnvironmentById(@PathVariable Long id) {
        Optional<EnvironmentDTO> environment = environmentService.getEnvironmentById(id);
        return environment.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/repository/{repositoryId}")
    public ResponseEntity<List<EnvironmentDTO>> getEnvironmentsByRepositoryId(@PathVariable Long repositoryId) {
        List<EnvironmentDTO> environments = environmentService.getEnvironmentsByRepositoryId(repositoryId);
        return ResponseEntity.ok(environments);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EnvironmentDTO> updateEnvironment(@PathVariable Long id, @RequestBody EnvironmentDTO environmentDTO) {
        Optional<EnvironmentDTO> updatedEnvironment = environmentService.updateEnvironment(id, environmentDTO);
        return updatedEnvironment.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
