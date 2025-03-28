package de.tum.cit.aet.helios.pullrequest.github;

import de.tum.cit.aet.helios.common.util.DateUtil;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.label.Label;
import de.tum.cit.aet.helios.label.LabelRepository;
import de.tum.cit.aet.helios.label.github.GitHubLabelConverter;
import de.tum.cit.aet.helios.pullrequest.PullRequestRepository;
import de.tum.cit.aet.helios.user.User;
import de.tum.cit.aet.helios.user.github.GitHubUserSyncService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Log4j2
@RequiredArgsConstructor
public class GitHubPullRequestSyncService {

  private final PullRequestRepository pullRequestRepository;
  private final GitRepoRepository gitRepoRepository;
  private final GitHubPullRequestConverter pullRequestConverter;
  private final LabelRepository labelRepository;
  private final GitHubLabelConverter gitHubLabelConverter;
  private final GitHubUserSyncService gitHubUserSyncService;

  /**
   * Processes a single GitHub pull request by updating or creating it in the local repository.
   * Manages associations with repositories, labels, milestones, authors, assignees, merged by
   * users, and requested reviewers.
   *
   * @param ghPullRequest the GitHub pull request to process
   */
  @Transactional
  public void processPullRequest(GHPullRequest ghPullRequest) {
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
      return;
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

    // Link new labels and remove labels that are not present anymore
    var ghLabels = ghPullRequest.getLabels();
    var resultLabels = new HashSet<Label>();
    ghLabels.forEach(
        ghLabel -> {
          var resultLabel =
              labelRepository
                  .findById(ghLabel.getId())
                  .orElseGet(
                      () -> {
                        var label = gitHubLabelConverter.convert(ghLabel);
                        label.setRepository(result.getRepository());
                        return labelRepository.save(label);
                      });
          resultLabels.add(resultLabel);
        });
    result.getLabels().clear();
    result.getLabels().addAll(resultLabels);

    // Link author
    try {
      var author = ghPullRequest.getUser();
      var resultAuthor = gitHubUserSyncService.processUser(author);
      if (resultAuthor != null) {
        result.setAuthor(resultAuthor);
      }
    } catch (IOException e) {
      log.error(
          "Failed to link author for pull request {}: {}", ghPullRequest.getId(), e.getMessage());
    }

    // Link assignees
    var assignees = ghPullRequest.getAssignees();
    var resultAssignees = new HashSet<User>();
    assignees.forEach(
        assignee -> {
          try {
            User assigned = gitHubUserSyncService.processUser(assignee);
            if (assigned != null) {
              resultAssignees.add(assigned);
            }
          } catch (Exception e) {
            log.error("Failed to sync assignee {}: {}", assignee.getLogin(), e.getMessage());
          }
        });
    result.setAssignees(resultAssignees);

    // Link merged by
    try {
      var mergedByUser = ghPullRequest.getMergedBy();
      if (mergedByUser == null) {
        result.setMergedBy(gitHubUserSyncService.getAnonymousUser());
      } else {
        var resultMergedBy = gitHubUserSyncService.processUser(mergedByUser);
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

      for (GHUser reviewer : requestedReviewers) {
        try {
          User user = gitHubUserSyncService.processUser(reviewer);
          if (user != null) {
            resultRequestedReviewers.add(user);
          }
        } catch (Exception e) {
          log.error(
              "Failed to link requested reviewer {}: {}", reviewer.getLogin(), e.getMessage(), e);
        }
      }

      result.setRequestedReviewers(resultRequestedReviewers);
    } catch (IOException e) {
      log.error(
          "Failed to link requested reviewers for pull request {}: {}",
          ghPullRequest.getId(),
          e.getMessage());
    }

    pullRequestRepository.save(result);
  }
}
