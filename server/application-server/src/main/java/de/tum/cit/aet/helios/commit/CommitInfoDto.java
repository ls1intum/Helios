package de.tum.cit.aet.helios.commit;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.helios.gitrepo.RepositoryInfoDto;
import de.tum.cit.aet.helios.user.UserInfoDto;
import java.time.OffsetDateTime;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CommitInfoDto(
    @NonNull String sha,
    UserInfoDto author,
    String message,
    OffsetDateTime authoredAt,
    RepositoryInfoDto repository) {

  public static CommitInfoDto fromCommit(Commit commit) {
    return new CommitInfoDto(
        commit.getSha(),
        UserInfoDto.fromUser(commit.getAuthor()),
        commit.getMessage(),
        commit.getAuthoredAt(),
        RepositoryInfoDto.fromRepository(commit.getRepository()));
  }
}
