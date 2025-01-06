package de.tum.cit.aet.helios.pullrequest.github;

import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.pullrequest.PullRequest;
import de.tum.cit.aet.helios.pullrequest.PullRequestRepository;
import de.tum.cit.aet.helios.user.User;
import de.tum.cit.aet.helios.user.UserRepository;
import de.tum.cit.aet.helios.user.github.GitHubUserConverter;
import de.tum.cit.aet.helios.util.DateUtil;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHDirection;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestQueryBuilder.Sort;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class GitHubPullRequestSyncService {

  private final PullRequestRepository pullRequestRepository;
  private final GitRepoRepository gitRepoRepository;
  private final UserRepository userRepository;
  private final GitHubPullRequestConverter pullRequestConverter;
  private final GitHubUserConverter userConverter;

  public GitHubPullRequestSyncService(
      PullRequestRepository pullRequestRepository,
      GitRepoRepository gitRepoRepository,
      UserRepository userRepository,
      GitHubPullRequestConverter pullRequestConverter,
      GitHubUserConverter userConverter) {
    this.pullRequestRepository = pullRequestRepository;
    this.gitRepoRepository = gitRepoRepository;
    this.userRepository = userRepository;
    this.pullRequestConverter = pullRequestConverter;
    this.userConverter = userConverter;
  }

  /**
   * Synchronizes all pull requests from the specified GitHub repositories.
   *
   * @param repositories the list of GitHub repositories to sync pull requests from
   * @param since an optional date to filter pull requests by their last update
   * @return a list of GitHub pull requests that were successfully fetched and processed
   */
  public List<GHPullRequest> syncPullRequestsOfAllRepositories(
      List<GHRepository> repositories, Optional<OffsetDateTime> since) {
    return repositories.stream()
        .map(repository -> syncPullRequestsOfRepository(repository, since))
        .flatMap(List::stream)
        .toList();
  }

  /**
   * Synchronizes all pull requests from a specific GitHub repository.
   *
   * @param repository the GitHub repository to sync pull requests from
   * @param since an optional date to filter pull requests by their last update
   * @return a list of GitHub pull requests that were successfully fetched and processed
   */
  public List<GHPullRequest> syncPullRequestsOfRepository(
      GHRepository repository, Optional<OffsetDateTime> since) {
    var iterator =
        repository
            .queryPullRequests()
            .state(GHIssueState.ALL)
            .sort(Sort.UPDATED)
            .direction(GHDirection.DESC)
            .list()
            .withPageSize(100)
            .iterator();

    var sinceDate = since.map(date -> Date.from(date.toInstant()));

    var pullRequests = new ArrayList<GHPullRequest>();
    while (iterator.hasNext()) {
      var ghPullRequests = iterator.nextPage();
      var keepPullRequests =
          ghPullRequests.stream()
              .filter(
                  pullRequest -> {
                    try {
                      return sinceDate.isEmpty()
                          || pullRequest.getUpdatedAt().after(sinceDate.get());
                    } catch (IOException e) {
                      log.error(
                          "Failed to filter pull request {}: {}",
                          pullRequest.getId(),
                          e.getMessage());
                      return false;
                    }
                  })
              .toList();

      pullRequests.addAll(keepPullRequests);
      if (keepPullRequests.size() != ghPullRequests.size()) {
        break;
      }
    }

    pullRequests.forEach(this::processPullRequest);
    return pullRequests;
  }

  /**
   * Processes a single GitHub pull request by updating or creating it in the local repository.
   * Manages associations with repositories, labels, milestones, authors, assignees, merged by
   * users, and requested reviewers.
   *
   * @param ghPullRequest the GitHub pull request to process
   * @return the updated or newly created PullRequest entity, or {@code null} if an error occurred
   */
  @Transactional
  public PullRequest processPullRequest(GHPullRequest ghPullRequest) {
    var result =
        pullRequestRepository
            .findById(ghPullRequest.getId())
            .map(
                pullRequest -> {
                  try {
                    if (pullRequest.getUpdatedAt() == null
                        || pullRequest
                            .getUpdatedAt()
                            .isBefore(
                                DateUtil.convertToOffsetDateTime(ghPullRequest.getUpdatedAt()))) {
                      return pullRequestConverter.update(ghPullRequest, pullRequest);
                    }
                    return pullRequest;
                  } catch (IOException e) {
                    log.error(
                        "Failed to update pull request {}: {}",
                        ghPullRequest.getId(),
                        e.getMessage());
                    return null;
                  }
                })
            .orElseGet(() -> pullRequestConverter.convert(ghPullRequest));

    if (result == null) {
      return null;
    }

    // Link with existing repository if not already linked
    if (result.getRepository() == null) {
      // Extract name with owner from the repository URL
      // Example: https://api.github.com/repos/ls1intum/Artemis/pulls/9463
      var nameWithOwner = ghPullRequest.getUrl().toString().split("/repos/")[1].split("/pulls")[0];
      var repository = gitRepoRepository.findByNameWithOwner(nameWithOwner);
      if (repository != null) {
        result.setRepository(repository);
      }
    }

    // Link author
    try {
      var author = ghPullRequest.getUser();
      var resultAuthor =
          userRepository
              .findById(author.getId())
              .orElseGet(() -> userRepository.save(userConverter.convert(author)));
      result.setAuthor(resultAuthor);
    } catch (IOException e) {
      log.error(
          "Failed to link author for pull request {}: {}", ghPullRequest.getId(), e.getMessage());
    }

    // Link assignees
    var assignees = ghPullRequest.getAssignees();
    var resultAssignees = new HashSet<User>();
    assignees.forEach(
        assignee -> {
          var resultAssignee =
              userRepository
                  .findById(assignee.getId())
                  .orElseGet(() -> userRepository.save(userConverter.convert(assignee)));
          resultAssignees.add(resultAssignee);
        });
    result.setAssignees(resultAssignees);

    // Link merged by
    try {
      var mergedByUser = ghPullRequest.getMergedBy();
      if (mergedByUser == null) {
        result.setMergedBy(
            userRepository
                .findById(Long.parseLong("-1"))
                .orElseGet(() -> userRepository.save(userConverter.convertToAnonymous())));
      } else {
        var resultMergedBy =
            userRepository
                .findById(ghPullRequest.getMergedBy().getId())
                .orElseGet(() -> userRepository.save(userConverter.convert(mergedByUser)));
        result.setMergedBy(resultMergedBy);
      }
    } catch (IOException e) {
      log.error(
          "Failed to link merged by user for pull request {}: {}",
          ghPullRequest.getId(),
          e.getMessage());
    }

    // Link requested reviewers
    try {
      List<GHUser> requestedReviewers = new ArrayList<>(ghPullRequest.getRequestedReviewers());

      // Add indirectly requested reviewers by team
      var requestedReviewersByTeam = ghPullRequest.getRequestedTeams();
      requestedReviewersByTeam.forEach(
          requestedTeam -> {
            try {
              requestedReviewers.addAll(requestedTeam.getMembers());
            } catch (IOException e) {
              log.error(
                  "Failed to link requested reviewers (by team) for pull request {}: {}",
                  ghPullRequest.getId(),
                  e.getMessage());
            }
          });

      var resultRequestedReviewers = new HashSet<User>();

      resultRequestedReviewers.addAll(
          requestedReviewers.stream()
              .map(
                  user ->
                      userRepository
                          .findById(user.getId())
                          .orElseGet(() -> userRepository.save(userConverter.convert(user))))
              .toList());
      result.setRequestedReviewers(resultRequestedReviewers);
    } catch (IOException e) {
      log.error(
          "Failed to link requested reviewers for pull request {}: {}",
          ghPullRequest.getId(),
          e.getMessage());
    }

    return pullRequestRepository.save(result);
  }
}
