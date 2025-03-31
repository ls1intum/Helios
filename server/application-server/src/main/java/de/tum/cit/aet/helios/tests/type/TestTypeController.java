package de.tum.cit.aet.helios.tests.type;

import de.tum.cit.aet.helios.config.security.annotations.EnforceAtLeastMaintainer;
import de.tum.cit.aet.helios.filters.RepositoryContext;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test-types")
@RequiredArgsConstructor
public class TestTypeController {
  private final TestTypeService testTypeService;

  @GetMapping
  @EnforceAtLeastMaintainer
  public List<TestTypeDto> getAllTestTypes() {
    Long repositoryId = RepositoryContext.getRepositoryId();
    return testTypeService.getAllTestTypes(repositoryId);
  }

  @PostMapping
  @EnforceAtLeastMaintainer
  @ResponseStatus(HttpStatus.CREATED)
  public TestTypeDto createTestType(@Valid @RequestBody TestTypeDto testTypeDto) {
    Long repositoryId = RepositoryContext.getRepositoryId();
    return testTypeService.createTestType(repositoryId, testTypeDto);
  }

  @PutMapping("/{testTypeId}")
  @EnforceAtLeastMaintainer
  public TestTypeDto updateTestType(
      @PathVariable Long testTypeId, @Valid @RequestBody TestTypeDto testTypeDto) {
    Long repositoryId = RepositoryContext.getRepositoryId();
    return testTypeService.updateTestType(repositoryId, testTypeId, testTypeDto);
  }

  @DeleteMapping("/{testTypeId}")
  @EnforceAtLeastMaintainer
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteTestType(@PathVariable Long testTypeId) {
    Long repositoryId = RepositoryContext.getRepositoryId();
    testTypeService.deleteTestType(repositoryId, testTypeId);
  }
}
