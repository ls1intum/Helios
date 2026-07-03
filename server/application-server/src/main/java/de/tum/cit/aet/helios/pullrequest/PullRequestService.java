package de.tum.cit.aet.helios.pullrequest;

import de.tum.cit.aet.helios.auth.AuthService;
import de.tum.cit.aet.helios.filters.RepositoryContext;
import de.tum.cit.aet.helios.issue.Issue;
import de.tum.cit.aet.helios.label.Label;
import de.tum.cit.aet.helios.pullrequest.pagination.PaginatedPullRequestsResponse;
import de.tum.cit.aet.helios.pullrequest.pagination.PullRequestPageRequest;
import de.tum.cit.aet.helios.pullrequest.pagination.PullRequestReviewFilterType;
import de.tum.cit.aet.helios.user.User;
import de.tum.cit.aet.helios.userpreference.UserPreference;
import de.tum.cit.aet.helios.userpreference.UserPreferenceRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Log4j2
@Service
@Transactional
@RequiredArgsConstructor
public class PullRequestService {

  private final PullRequestRepository pullRequestRepository;
  private final UserPreferenceRepository userPreferenceRepository;
  private final AuthService authService;

  public List<PullRequestBaseInfoDto> getAllPullRequests() {
    final Optional<UserPreference> userPreference =
        authService.isLoggedIn()
            ? userPreferenceRepository.findByUser(authService.getUserFromGithubId())
            : Optional.empty();
    Long repositoryId = RepositoryContext.getRepositoryId();
    if (repositoryId == null) {
      return List.of();
    }
    return pullRequestRepository.findByRepositoryRepositoryIdOrderByUpdatedAtDesc(repositoryId)
        .stream()
        .map((pr) -> PullRequestBaseInfoDto.fromPullRequestAndUserPreference(pr, userPreference))
        .sorted(
            (pr1, pr2) -> {
              if (pr1.isPinned() && !pr2.isPinned()) {
                return -1;
              } else if (!pr1.isPinned() && pr2.isPinned()) {
                return 1;
              } else {
                return 0;
              }
            })
        .collect(Collectors.toList());
  }

  public PaginatedPullRequestsResponse getPaginatedPullRequests(
      PullRequestPageRequest pageRequest) {
    log.debug(
        "Input parameters - Filter: {}, Page: {}, Size: {}, Search Term: {}, "
            + "Author: {}, Assignee: {}, NoAssignee: {}, LabelId: {}, NoLabel: {}, "
            + "ReviewState: {}, RequestedReviewerLogin: {}",
        pageRequest.getFilterType(),
        pageRequest.getPage(),
        pageRequest.getSize(),
        pageRequest.getSearchTerm(),
        pageRequest.getAuthor(),
        pageRequest.getAssignee(),
        pageRequest.getNoAssignee(),
        pageRequest.getLabelId(),
        pageRequest.getNoLabel(),
        pageRequest.getReviewState(),
        pageRequest.getRequestedReviewer());

    final String currentUserId = authService.isLoggedIn() ? authService.getGithubId() : null;

    Optional<UserPreference> prefOpt =
        currentUserId != null
            ? userPreferenceRepository.findByUser(authService.getUserFromGithubId())
            : Optional.empty();

    Long repositoryId = RepositoryContext.getRepositoryId();
    if (repositoryId == null) {
      return new PaginatedPullRequestsResponse(
          List.of(), List.of(), pageRequest.getPage(), pageRequest.getSize(), 0, 0);
    }

    /* ---------- pinned ---------- */
    List<PullRequestBaseInfoDto> pinnedDtos =
        prefOpt.map(UserPreference::getFavouritePullRequests).orElseGet(HashSet::new).stream()
            .filter(
                pr -> {
                  return pr.getRepository().getRepositoryId().equals(repositoryId);
                })
            .sorted(Comparator.comparing(PullRequest::getUpdatedAt).reversed())
            .map(pr -> PullRequestBaseInfoDto.fromPullRequestAndUserPreference(pr, prefOpt))
            .toList();

    /* ---------- non-pinned ---------- */
    Specification<PullRequest> nonPinnedSpec =
        buildNonPinnedPullRequestSpecification(pageRequest, currentUserId, repositoryId);

    long totalNonPinned = pullRequestRepository.count(nonPinnedSpec);
    log.debug(
        "Total Elements Breakdown - Pinned: {}, Non-Pinned: {}", pinnedDtos.size(), totalNonPinned);

    // Create pageable for non-pinned PRs
    Pageable pageable =
        PageRequest.of(
            pageRequest.getPage() - 1,
            pageRequest.getSize(),
            resolveSort(pageRequest));

    // Fetch non-pinned PRs
    List<PullRequestBaseInfoDto> pageDtos =
        pullRequestRepository
            .findAll(nonPinnedSpec, pageable)
            .map(pr -> PullRequestBaseInfoDto.fromPullRequestAndUserPreference(pr, prefOpt))
            .getContent();

    log.debug("Added Non-Pinned Items - Count: {}", pageDtos.size());

    int totalPages = (int) Math.ceil((double) totalNonPinned / pageRequest.getSize());

    return new PaginatedPullRequestsResponse(
        pinnedDtos,
        pageDtos,
        pageRequest.getPage(),
        pageRequest.getSize(),
        totalNonPinned,
        totalPages);
  }

  private Specification<PullRequest> buildNonPinnedPullRequestSpecification(
      PullRequestPageRequest pageRequest, String currentUserId, Long repositoryId) {
    return (root, query, cb) -> {
      query.distinct(true);
      List<Predicate> predicates = new ArrayList<>();

      // Scope to the current repository (explicit; replaces the ambient gitRepositoryFilter).
      predicates.add(cb.equal(root.get("repository").get("repositoryId"), repositoryId));

      // Add predicate for pinned status
      if (currentUserId != null) {
        // Create a subquery to find pinned PRs
        Subquery<Long> pinnedSubquery = query.subquery(Long.class);
        Root<UserPreference> userPrefRoot = pinnedSubquery.from(UserPreference.class);
        Join<UserPreference, User> userJoin = userPrefRoot.join("user");
        Join<UserPreference, PullRequest> prJoin = userPrefRoot.join("favouritePullRequests");

        pinnedSubquery
            .select(prJoin.get("id"))
            .where(cb.equal(userJoin.get("id"), Long.valueOf(currentUserId)));

        // For non-pinned, require that they are NOT in the favourites
        predicates.add(cb.not(root.get("id").in(pinnedSubquery)));
      }

      // Add search term predicate if provided
      if (pageRequest.getSearchTerm() != null && !pageRequest.getSearchTerm().trim().isEmpty()) {
        String searchTerm = "%" + pageRequest.getSearchTerm().toLowerCase() + "%";
        predicates.add(
            cb.or(
                cb.like(cb.lower(root.get("title")), searchTerm),
                cb.like(cb.toString(root.get("number")), searchTerm)));
      }

      if (StringUtils.hasText(pageRequest.getAuthor())) {
        Join<PullRequest, User> authorJoin = root.join("author", JoinType.INNER);
        predicates.add(
            cb.equal(
                cb.lower(authorJoin.get("login")),
                pageRequest.getAuthor().trim().toLowerCase(Locale.ROOT)));
      }

      if (StringUtils.hasText(pageRequest.getAssignee())) {
        Join<PullRequest, User> assigneeJoin = root.join("assignees", JoinType.INNER);
        predicates.add(
            cb.equal(
                cb.lower(assigneeJoin.get("login")),
                pageRequest.getAssignee().trim().toLowerCase(Locale.ROOT)));
      }

      if (Boolean.TRUE.equals(pageRequest.getNoAssignee())) {
        predicates.add(cb.isEmpty(root.get("assignees")));
      }

      if (pageRequest.getLabelId() != null) {
        Join<PullRequest, Label> labelJoin = root.join("labels", JoinType.INNER);
        predicates.add(cb.equal(labelJoin.get("id"), pageRequest.getLabelId()));
      }

      if (Boolean.TRUE.equals(pageRequest.getNoLabel())) {
        predicates.add(cb.isEmpty(root.get("labels")));
      }

      PullRequestReviewFilterType reviewState = pageRequest.getReviewState();
      if (reviewState != null) {
        switch (reviewState) {
          case NONE:
            predicates.add(cb.isEmpty(root.get("requestedReviewers")));
            break;
          case REQUIRED:
            predicates.add(cb.isNotEmpty(root.get("requestedReviewers")));
            break;
          default:
            break;
        }
      }

      String requestedReviewer = pageRequest.getRequestedReviewer();
      if (requestedReviewer != null && !requestedReviewer.isBlank()) {
        Join<PullRequest, User> requestedReviewerJoin =
            root.join("requestedReviewers", JoinType.INNER);
        predicates.add(
            cb.equal(
                cb.lower(requestedReviewerJoin.get("login")),
                requestedReviewer.trim().toLowerCase(Locale.ROOT)));
      }

      // Add filter type predicate
      switch (pageRequest.getFilterType()) {
        case OPEN:
          predicates.add(cb.equal(root.get("state"), Issue.State.OPEN));
          break;
        case OPEN_READY_FOR_REVIEW:
          predicates.add(
              cb.and(
                  cb.equal(root.get("state"), Issue.State.OPEN),
                  cb.equal(root.get("isDraft"), false)));
          break;
        case DRAFT:
          predicates.add(
              cb.and(
                  cb.equal(root.get("state"), Issue.State.OPEN),
                  cb.equal(root.get("isDraft"), true)));
          break;
        case MERGED:
          predicates.add(
              cb.and(
                  cb.equal(root.get("state"), Issue.State.CLOSED),
                  cb.equal(root.get("isMerged"), true)));
          break;
        case CLOSED:
          predicates.add(cb.equal(root.get("state"), Issue.State.CLOSED));
          break;
        case USER_AUTHORED:
          if (currentUserId != null) {
            Join<PullRequest, User> authorJoin = root.join("author", JoinType.INNER);
            predicates.add(cb.equal(authorJoin.get("id"), Long.valueOf(currentUserId)));
          }
          break;
        case ASSIGNED_TO_USER:
          if (currentUserId != null) {
            Join<PullRequest, User> assigneeJoin = root.join("assignees", JoinType.INNER);
            predicates.add(cb.equal(assigneeJoin.get("id"), Long.valueOf(currentUserId)));
          }
          break;
        case REVIEW_REQUESTED:
          if (currentUserId != null) {
            Join<PullRequest, User> reviewerJoin = root.join("requestedReviewers", JoinType.INNER);
            predicates.add(cb.equal(reviewerJoin.get("id"), Long.valueOf(currentUserId)));
          }
          break;
        case ALL:
        default:
          // No additional predicates needed
          break;
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  private Sort resolveSort(PullRequestPageRequest pageRequest) {
    Sort defaultSort = Sort.by(Sort.Direction.DESC, "updatedAt");

    String sortField = pageRequest.getSortField();
    if (sortField == null || sortField.isBlank()) {
      return defaultSort;
    }

    String property;
    switch (sortField) {
      case "updatedAt":
        property = "updatedAt";
        break;
      case "createdAt":
        property = "createdAt";
        break;
      default:
        return defaultSort;
    }

    Sort.Direction direction =
        "asc".equalsIgnoreCase(pageRequest.getSortDirection())
            ? Sort.Direction.ASC
            : Sort.Direction.DESC;
    return Sort.by(direction, property);
  }

  public Optional<PullRequestInfoDto> getPullRequestById(Long id) {
    Long repositoryId = RepositoryContext.getRepositoryId();
    Optional<PullRequest> pullRequest =
        repositoryId == null
            ? pullRequestRepository.findById(id)
            : pullRequestRepository.findByIdAndRepositoryRepositoryId(id, repositoryId);
    return pullRequest.map(PullRequestInfoDto::fromPullRequest);
  }

  public List<PullRequestInfoDto> getPullRequestByRepositoryId(Long repositoryId) {
    return pullRequestRepository
        .findByRepositoryRepositoryIdOrderByUpdatedAtDesc(repositoryId)
        .stream()
        .map(PullRequestInfoDto::fromPullRequest)
        .collect(Collectors.toList());
  }

  public void setPrPinnedByNumberAndUserId(Long prId, Boolean isPinned) {
    final UserPreference userPreference =
        userPreferenceRepository
            .findByUser(authService.getUserFromGithubId())
            .orElseGet(
                () -> {
                  final UserPreference pref = new UserPreference();
                  pref.setUser(authService.getUserFromGithubId());
                  pref.setFavouriteBranches(new HashSet<>());
                  pref.setFavouritePullRequests(new HashSet<>());
                  return userPreferenceRepository.saveAndFlush(pref);
                });

    if (!isPinned) {
      userPreference.getFavouritePullRequests().removeIf(pr -> pr.getId().equals(prId));
    } else {
      final PullRequest pullRequest =
          pullRequestRepository
              .findById(prId)
              .orElseThrow(() -> new EntityNotFoundException("PR " + prId + " not found"));
      userPreference.getFavouritePullRequests().add(pullRequest);
    }
    userPreferenceRepository.save(userPreference);
  }

  public Optional<PullRequestInfoDto> getPullRequestByRepositoryIdAndNumber(
      Long repoId, Integer number) {
    return pullRequestRepository
        .findByRepositoryRepositoryIdAndNumber(repoId, number)
        .map(PullRequestInfoDto::fromPullRequest);
  }

  public PullRequestFilterOptionsDto getPullRequestFilterOptionsByRepositoryId(Long repositoryId) {
    List<PullRequestFilterUserOptionDto> authors =
        pullRequestRepository.findDistinctAuthorsByRepositoryId(repositoryId).stream()
            .map(PullRequestFilterUserOptionDto::fromUser)
            .sorted(Comparator.comparing(user -> user.login().toLowerCase(Locale.ROOT)))
            .toList();

    List<PullRequestFilterUserOptionDto> assignees =
        pullRequestRepository.findDistinctAssigneesByRepositoryId(repositoryId).stream()
            .map(PullRequestFilterUserOptionDto::fromUser)
            .sorted(Comparator.comparing(user -> user.login().toLowerCase(Locale.ROOT)))
            .toList();

    List<PullRequestFilterUserOptionDto> reviewers =
        pullRequestRepository.findDistinctReviewersByRepositoryId(repositoryId).stream()
            .map(PullRequestFilterUserOptionDto::fromUser)
            .sorted(Comparator.comparing(user -> user.login().toLowerCase(Locale.ROOT)))
            .toList();

    List<PullRequestFilterLabelOptionDto> labels =
        pullRequestRepository.findDistinctLabelsByRepositoryId(repositoryId).stream()
            .map(PullRequestFilterLabelOptionDto::fromLabel)
            .sorted(Comparator.comparing(label -> label.name().toLowerCase(Locale.ROOT)))
            .toList();

    return new PullRequestFilterOptionsDto(authors, assignees, reviewers, labels);
  }
}
