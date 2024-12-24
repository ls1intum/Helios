package de.tum.cit.aet.helios.pullrequest;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PullRequestRepository extends JpaRepository<PullRequest, Long> {

  Optional<PullRequest> findByRepositoryIdAndHeadRefNameOrHeadSha(Long id, String ref, String sha);

  List<PullRequest> findByRepositoryId(Long repositoryId);

  Optional<PullRequest> findByRepositoryIdAndNumber(Long repositoryId, Integer number);
}
