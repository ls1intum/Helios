package de.tum.cit.aet.helios.pullrequest;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PullRequestRepository extends JpaRepository<PullRequest, Long> {

  Optional<PullRequest> findByRepositoryRepositoryIdAndHeadRefNameOrHeadSha(Long id, String ref,
                                                                            String sha);

  List<PullRequest> findByRepositoryRepositoryId(Long repositoryId);

  Optional<PullRequest> findByRepositoryRepositoryIdAndNumber(Long repositoryId, Integer number);
}
