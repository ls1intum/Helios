package de.tum.cit.aet.helios.branch;

import de.tum.cit.aet.helios.tag.Tag;
import de.tum.cit.aet.helios.tag.TagRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class BranchService {

  private final BranchRepository branchRepository;
  private final TagRepository tagRepository;

  public BranchService(BranchRepository branchRepository, TagRepository tagRepository) {
    this.branchRepository = branchRepository;
    this.tagRepository = tagRepository;
  }

  public List<BranchInfoDto> getAllBranches() {
    return branchRepository.findAll().stream()
        .map(BranchInfoDto::fromBranch)
        .collect(Collectors.toList());
  }

  public Optional<BranchInfoDto> getBranchByName(String name) {
    return branchRepository.findByName(name).map(BranchInfoDto::fromBranch);
  }

  @Transactional
  public void deleteBranchByNameAndRepositoryId(String name, Long repositoryId) {
    branchRepository.deleteByNameAndRepositoryRepositoryId(name, repositoryId);
  }

  public List<BranchInfoDto> getBranchesByRepositoryId(Long repositoryId) {
    return branchRepository.findByRepositoryRepositoryId(repositoryId).stream()
        .map(BranchInfoDto::fromBranch)
        .collect(Collectors.toList());
  }

  public Optional<BranchInfoDto> getBranchInfo(Long repositoryId, String name) {
    return branchRepository
        .findByRepositoryRepositoryIdAndName(repositoryId, name)
        .map(BranchInfoDto::fromBranch);
  }

  public Optional<BranchDetailsDto> getBranchByRepositoryIdAndName(Long repositoryId, String name) {
    return branchRepository
        .findByRepositoryRepositoryIdAndName(repositoryId, name)
        .map(
            branch ->
                BranchDetailsDto.fromBranch(
                    branch,
                    tagRepository
                        .findByRepositoryRepositoryIdAndCommitSha(
                            repositoryId, branch.getCommitSha())
                        .map(Tag::getName)
                        .orElseGet(() -> null)));
  }
}
