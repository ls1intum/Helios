package de.tum.cit.aet.helios.environments.environments;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
@RequestMapping("/api/environments")
public class EnvironmentController {

    private final EnvironmentService environmentService;


    public EnvironmentController(EnvironmentService environmentService) {
        this.environmentService = environmentService;
    }

    @GetMapping
    public ResponseEntity<List<EnvironmentInfoDTO>> getAllEnvironments() {
        List<EnvironmentInfoDTO> environments = environmentService.getAllEnvironemnts();
        if (environments.isEmpty()) {
            return ResponseEntity.notFound().build();
        } else {
            return ResponseEntity.ok(environments);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<EnvironmentInfoDTO> getEnvironmentById(@PathVariable Long id) {
        return environmentService.getEnvironmentById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // @PostMapping()
    // public EnvironmentInfo postMethodName(@RequestBody EnvironmentInfoDTO entity) {
        
        
    //     return environmentService.;
    // }
    
    
}