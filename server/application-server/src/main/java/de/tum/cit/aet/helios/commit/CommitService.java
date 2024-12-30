package de.tum.cit.aet.helios.commit;

import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class CommitService {

  private final CommitRepository commitRepository;

  public CommitService(CommitRepository commitRepository) {
    this.commitRepository = commitRepository;
  }

  public Optional<CommitInfoDto> getCommitByShaAndRepositoryId(String sha, Long repositoryId) {
    return commitRepository
        .findByShaAndRepositoryId(sha, repositoryId)
        .map(CommitInfoDto::fromCommit);
  }
}
