package de.tum.cit.aet.helios.pullrequest;

import de.tum.cit.aet.helios.issue.Issue;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PullRequestRepository extends JpaRepository<PullRequest, Long> {

  Optional<PullRequest> findByRepositoryRepositoryIdAndHeadRefNameOrHeadSha(Long id, String ref,
                                                                            String sha);

  Optional<PullRequest> findByRepositoryRepositoryIdAndHeadRefName(Long id, String ref);

  Optional<PullRequest> findByRepositoryRepositoryIdAndHeadRefNameAndState(Long repositoryId,
                                                                           String headRefName,
                                                                           Issue.State state);

  List<PullRequest> findAllByState(Issue.State state);

  List<PullRequest> findAllByOrderByUpdatedAtDesc();

  List<PullRequest> findByRepositoryRepositoryIdOrderByUpdatedAtDesc(Long repositoryId);

  Optional<PullRequest> findByRepositoryRepositoryIdAndNumber(Long repositoryId, Integer number);
}
