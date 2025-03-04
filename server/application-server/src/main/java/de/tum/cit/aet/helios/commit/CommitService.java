package de.tum.cit.aet.helios.commit;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommitService {

  private final CommitRepository commitRepository;

  public Optional<CommitInfoDto> getCommitByShaAndRepositoryId(String sha, Long repositoryId) {
    return commitRepository
        .findByShaAndRepositoryRepositoryId(sha, repositoryId)
        .map(CommitInfoDto::fromCommit);
  }
}
