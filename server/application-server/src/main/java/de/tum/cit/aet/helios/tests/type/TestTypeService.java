package de.tum.cit.aet.helios.tests.type;

import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.workflow.Workflow;
import de.tum.cit.aet.helios.workflow.WorkflowRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TestTypeService {
  private final TestTypeRepository testTypeRepository;
  private final GitRepoRepository repoRepository;
  private final WorkflowRepository workflowRepository;

  public List<TestTypeDto> getAllTestTypes(Long repositoryId) {
    return testTypeRepository.findAllByRepositoryRepositoryId(repositoryId).stream()
        .map(TestTypeDto::fromTestType)
        .collect(Collectors.toList());
  }

  public TestTypeDto createTestType(Long repositoryId, TestTypeDto dto) {
    if (testTypeRepository.existsByNameAndRepositoryRepositoryId(dto.name(), repositoryId)) {
      throw new TestTypeNameConflictException(
          "Test type with name '" + dto.name() + "' already exists");
    }

    TestType testType = new TestType();
    testType.setName(dto.name());
    testType.setArtifactName(dto.artifactName());

    GitRepository repo =
        repoRepository
            .findByRepositoryId(repositoryId)
            .orElseThrow(() -> new IllegalArgumentException("Repository not found"));
    testType.setRepository(repo);

    Workflow workflow =
        workflowRepository
            .findById(dto.workflowId())
            .orElseThrow(() -> new IllegalArgumentException("Workflow not found"));

    if (!workflow.getRepository().getRepositoryId().equals(repositoryId)) {
      throw new IllegalArgumentException("Workflow does not belong to this repository");
    }

    testType.setWorkflow(workflow);

    return TestTypeDto.fromTestType(testTypeRepository.save(testType));
  }

  public TestTypeDto updateTestType(Long repositoryId, Long testTypeId, TestTypeDto dto) {
    if (testTypeRepository.existsByNameAndRepositoryRepositoryIdAndIdNot(
        dto.name(), repositoryId, testTypeId)) {
      throw new TestTypeNameConflictException(
          "Test type with name '" + dto.name() + "' already exists");
    }

    TestType testType =
        testTypeRepository
            .findByIdAndRepositoryRepositoryId(testTypeId, repositoryId)
            .orElseThrow();

    updateTestTypeFromDto(testType, dto, repositoryId);
    return TestTypeDto.fromTestType(testTypeRepository.save(testType));
  }

  public void deleteTestType(Long repositoryId, Long testTypeId) {
    TestType testType =
        testTypeRepository
            .findByIdAndRepositoryRepositoryId(testTypeId, repositoryId)
            .orElseThrow();

    testTypeRepository.delete(testType);
  }

  private void updateTestTypeFromDto(TestType testType, TestTypeDto dto, Long repositoryId) {
    testType.setName(dto.name());
    testType.setArtifactName(dto.artifactName());

    GitRepository repo =
        repoRepository
            .findByRepositoryId(repositoryId)
            .orElseThrow(() -> new IllegalArgumentException("Repository not found"));
    testType.setRepository(repo);

    Workflow workflow =
        workflowRepository
            .findById(dto.workflowId())
            .orElseThrow(() -> new IllegalArgumentException("Workflow not found"));
    if (!workflow.getRepository().getRepositoryId().equals(repositoryId)) {
      throw new IllegalArgumentException("Workflow does not belong to this repository");
    }
    testType.setWorkflow(workflow);
  }
}
