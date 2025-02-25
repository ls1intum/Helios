package de.tum.cit.aet.helios.branch;

import de.tum.cit.aet.helios.auth.AuthService;
import de.tum.cit.aet.helios.releasecandidate.ReleaseCandidate;
import de.tum.cit.aet.helios.releasecandidate.ReleaseCandidateRepository;
import de.tum.cit.aet.helios.userpreference.UserPreference;
import de.tum.cit.aet.helios.userpreference.UserPreferenceRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class BranchService {

  private final BranchRepository branchRepository;
  private final ReleaseCandidateRepository releaseCandidateRepository;
  private final UserPreferenceRepository userPreferenceRepository;
  private final AuthService authService;

  public List<BranchInfoDto> getAllBranches() {
    final Optional<UserPreference> userPreference =
        authService.isLoggedIn()
            ? userPreferenceRepository.findByUser(authService.getUserFromGithubId())
            : Optional.empty();
    return branchRepository.findAll().stream()
        .map((branch) -> BranchInfoDto.fromBranchAndUserPreference(branch, userPreference))
        .sorted(
            (pr1, pr2) -> {
              if (pr1.isPinned() && !pr2.isPinned()) {
                return -1;
              } else if (!pr1.isPinned() && pr2.isPinned()) {
                return 1;
              } else {
                return pr2.updatedAt().compareTo(pr1.updatedAt());
              }
            })
        .collect(Collectors.toList());
  }

  @Transactional
  public void deleteBranchByNameAndRepositoryId(String name, Long repositoryId) {
    branchRepository.deleteByNameAndRepositoryRepositoryId(name, repositoryId);
  }

  public Optional<BranchDetailsDto> getBranchByRepositoryIdAndName(Long repositoryId, String name) {
    return branchRepository
        .findByRepositoryRepositoryIdAndName(repositoryId, name)
        .map(
            branch ->
                BranchDetailsDto.fromBranch(
                    branch,
                    releaseCandidateRepository
                        .findByRepositoryRepositoryIdAndCommitSha(
                            repositoryId, branch.getCommitSha())
                        .map(ReleaseCandidate::getName)
                        .orElseGet(() -> null)));
  }

  public void setBranchPinnedByRepositoryIdAndName(Long repoId, String name, Boolean isPinned) {
    final UserPreference userPreference =
        userPreferenceRepository
            .findByUser(authService.getUserFromGithubId())
            .orElseGet(
                () -> {
                  final UserPreference pref = new UserPreference();
                  pref.setUser(authService.getUserFromGithubId());
                  pref.setFavouriteBranches(new HashSet<>());
                  pref.setFavouritePullRequests(new HashSet<>());
                  return userPreferenceRepository.saveAndFlush(pref);
                });

    if (!isPinned) {
      userPreference
          .getFavouriteBranches()
          .removeIf(
              branch ->
                  branch.getRepository().getRepositoryId().equals(repoId)
                      && branch.getName().equals(name));
    } else {
      final Branch branch =
          branchRepository
              .findByNameAndRepositoryRepositoryId(name, repoId)
              .orElseThrow(() -> new EntityNotFoundException("Branch " + name + " not found"));
      userPreference.getFavouriteBranches().add(branch);
    }
    userPreferenceRepository.save(userPreference);
  }
}
