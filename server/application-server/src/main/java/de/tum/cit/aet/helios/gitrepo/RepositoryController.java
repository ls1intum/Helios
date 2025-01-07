package de.tum.cit.aet.helios.gitrepo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repository")
public class RepositoryController {

  private final RepositoryService repositoryService;

  public RepositoryController(RepositoryService repositoryService) {
    this.repositoryService = repositoryService;
  }

  @GetMapping
  public ResponseEntity<Iterable<RepositoryInfoDto>> getRepositories() {
    return ResponseEntity.ok(repositoryService.getRepositories());
  }

  @GetMapping("/{id}")
  public ResponseEntity<RepositoryInfoDto> getRepositoryById(@PathVariable Long id) {
    return repositoryService
        .getRepositoryById(id)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
