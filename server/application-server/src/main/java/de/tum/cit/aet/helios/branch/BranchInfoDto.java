package de.tum.cit.aet.helios.branch;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.helios.commit.Commit;
import de.tum.cit.aet.helios.commit.CommitRepository;
import de.tum.cit.aet.helios.gitrepo.RepositoryInfoDto;
import de.tum.cit.aet.helios.user.UserInfoDto;
import de.tum.cit.aet.helios.userpreference.UserPreference;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BranchInfoDto(
    @NonNull String name,
    @NonNull String commitSha,
    int aheadBy,
    int behindBy,
    boolean isDefault,
    boolean isProtected,
    boolean isPinned,
    OffsetDateTime updatedAt,
    UserInfoDto updatedBy,
    RepositoryInfoDto repository) {

  public static BranchInfoDto fromBranchAndUserPreference(
      Branch branch, Optional<UserPreference> userPreference, CommitRepository commitRepository) {

    Optional<Commit> latestCommit = commitRepository.findByShaAndRepository(
        branch.getCommitSha(), branch.getRepository());

    return new BranchInfoDto(
        branch.getName(),
        branch.getCommitSha(),
        branch.getAheadBy(),
        branch.getBehindBy(),
        branch.isDefault(),
        branch.isProtection(),
        userPreference.map(up -> up.getFavouriteBranches().contains(branch)).orElseGet(() -> false),
        latestCommit.map(Commit::getAuthoredAt).orElse(branch.getUpdatedAt()),
        latestCommit.map(c -> UserInfoDto.fromUser(c.getAuthor()))
            .orElseGet(() -> UserInfoDto.fromUser(branch.getUpdatedBy())),
        RepositoryInfoDto.fromRepository(branch.getRepository()));
  }

  public static BranchInfoDto fromBranch(
      Branch branch, CommitRepository commitRepository) {
    return fromBranchAndUserPreference(branch, Optional.empty(), commitRepository);
  }
}
