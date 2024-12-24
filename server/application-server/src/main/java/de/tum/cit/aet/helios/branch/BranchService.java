package de.tum.cit.aet.helios.branch;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BranchService {

  private final BranchRepository branchRepository;

  public BranchService(BranchRepository branchRepository) {
    this.branchRepository = branchRepository;
  }

  public List<BranchInfoDto> getAllBranches() {
    return branchRepository.findAll().stream()
        .map(BranchInfoDto::fromBranch)
        .collect(Collectors.toList());
  }

  public Optional<BranchInfoDto> getBranchById(Long id) {
    return branchRepository.findById(id).map(BranchInfoDto::fromBranch);
  }

  @Transactional
  public void deleteBranchByNameAndRepositoryId(String name, Long repositoryId) {
    branchRepository.deleteByNameAndRepositoryId(name, repositoryId);
  }

  public List<BranchInfoDto> getBranchesByRepositoryId(Long repositoryId) {
    return branchRepository.findByRepositoryId(repositoryId).stream()
        .map(BranchInfoDto::fromBranch)
        .collect(Collectors.toList());
  }

  public Optional<BranchInfoDto> getBranchInfo(Long repositoryId, String name) {
    return branchRepository
        .findByRepositoryIdAndName(repositoryId, name)
        .map(BranchInfoDto::fromBranch);
  }

  public Optional<Branch> getBranchByRepositoryIdAndName(Long repositoryId, String name) {
    return branchRepository.findByRepositoryIdAndName(repositoryId, name);
  }
}
