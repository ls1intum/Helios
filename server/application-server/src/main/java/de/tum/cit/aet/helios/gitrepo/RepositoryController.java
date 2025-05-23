package de.tum.cit.aet.helios.gitrepo;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/repository")
public class RepositoryController {

  private final RepositoryService repositoryService;

  @GetMapping
  public ResponseEntity<List<RepositoryInfoDto>> getAllRepositories() {
    return ResponseEntity.ok(repositoryService.getAllRepositories());
  }

  @GetMapping("/{id}")
  public ResponseEntity<RepositoryInfoDto> getRepositoryById(@PathVariable Long id) {
    return repositoryService
        .getRepositoryById(id)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
