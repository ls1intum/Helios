package de.tum.cit.aet.helios.pullrequest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface PullRequestRepository extends JpaRepository<PullRequest, Long> {

}