package de.tum.cit.aet.helios.pullrequest;

import de.tum.cit.aet.helios.auth.AuthService;
import de.tum.cit.aet.helios.issue.Issue;
import de.tum.cit.aet.helios.pagination.PageResponse;
import de.tum.cit.aet.helios.pullrequest.pagination.PullRequestPageRequest;
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    return pullRequestRepository.findAllByOrderByUpdatedAtDesc().stream()
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

  public PageResponse<PullRequestBaseInfoDto> getPaginatedPullRequests(
      PullRequestPageRequest pageRequest) {
    log.debug("Starting pagination for filter: {}, page: {}, size: {}",
        pageRequest.getFilterType(), pageRequest.getPage(), pageRequest.getSize());

    final String currentUserId = authService.isLoggedIn()
        ? authService.getGithubId()
        : null;

    final Optional<UserPreference> userPreference =
        authService.isLoggedIn()
            ? userPreferenceRepository.findByUser(authService.getUserFromGithubId())
            : Optional.empty();

    // First, get all pinned pull requests that match the filter criteria
    Specification<PullRequest> pinnedSpec = buildSpecification(pageRequest, currentUserId, true);
    List<PullRequest> pinnedPullRequests = pullRequestRepository.findAll(pinnedSpec);

    // Convert pinned PRs to DTOs
    List<PullRequestBaseInfoDto> pinnedDtos = pinnedPullRequests.stream()
        .map(pr -> PullRequestBaseInfoDto.fromPullRequestAndUserPreference(pr, userPreference))
        .collect(Collectors.toList());

    log.debug("Found {} pinned PRs matching filter", pinnedDtos.size());
    if (!pinnedDtos.isEmpty()) {
      log.debug("Pinned PR IDs: {}",
          pinnedDtos.stream().map(PullRequestBaseInfoDto::id).collect(Collectors.toList()));
    }

    // Calculate how many regular (non-pinned) items we need for this page
    int remainingItemsNeeded = pageRequest.getSize();
    log.debug("Remaining items needed: {}", remainingItemsNeeded);
    int offset = 0;

    // If we're on the first page, adjust for pinned items
    if (pageRequest.getPage() == 0) {
      log.debug("Adjusting for pinned items on first page");
      remainingItemsNeeded -= pinnedDtos.size();
      if (remainingItemsNeeded < 0) {
        remainingItemsNeeded = 0; // Ensure we don't get negative
      }

      // If we have more pinned items than page size, we need to handle that
      if (remainingItemsNeeded <= 0) {
        // In this case, we have more pinned items than can fit on a page
        // We'll need to paginate the pinned items themselves
        int pinnedStartIndex = 0;
        int pinnedEndIndex = Math.min(pageRequest.getSize(), pinnedDtos.size());
        PageResponse<PullRequestBaseInfoDto> response = new PageResponse<>();
        response.setContent(pinnedDtos.subList(pinnedStartIndex, pinnedEndIndex));
        response.setPage(pageRequest.getPage());
        response.setSize(pageRequest.getSize());
        response.setTotalElements(
            pinnedDtos.size() + countNonPinnedMatchingPRs(pageRequest, currentUserId));
        response.setTotalPages(
            (int) Math.ceil((double) response.getTotalElements() / pageRequest.getSize()));

        log.debug("Returning {} pinned PRs only (page {}/{})",
            response.getContent().size(), response.getPage() + 1, response.getTotalPages());
        return response;
      }
    } else {
      log.debug("Adjusting for pinned items on subsequent pages");
      // For pages after the first, we need to adjust the offset to account for pinned items
      offset = (pageRequest.getPage() * pageRequest.getSize()) - pinnedDtos.size();
      log.debug("Adjusted offset: {}", offset);
      if (offset >= 0) {
        log.debug("Returning pinned PRs only (page 1)");
        // We're still paginating through the pinned items
        int pinnedStartIndex = Math.abs(offset);
        int pinnedEndIndex = Math.min(pinnedStartIndex + pageRequest.getSize(), pinnedDtos.size());
        log.debug("Pinned start index: {}, end index: {}", pinnedStartIndex, pinnedEndIndex);
        PageResponse<PullRequestBaseInfoDto> response = new PageResponse<>();
        response.setContent(pinnedDtos.subList(pinnedStartIndex, pinnedEndIndex));
        response.setPage(pageRequest.getPage());
        response.setSize(pageRequest.getSize());
        response.setTotalElements(
            pinnedDtos.size() + countNonPinnedMatchingPRs(pageRequest, currentUserId));
        response.setTotalPages(
            (int) Math.ceil((double) response.getTotalElements() / pageRequest.getSize()));

        log.debug("Returning paginated pinned PRs (page {}/{})",
            response.getPage() + 1, response.getTotalPages());
        return response;
      }
      offset = Math.max(0, offset);
      log.debug("Adjusted offset: {}", offset);
    }

    log.debug("Remaining items needed after adjusting for pinned: {}", remainingItemsNeeded);

    // Only get non-pinned PRs if we need them (remainingItemsNeeded > 0)
    List<PullRequestBaseInfoDto> nonPinnedDtos = new ArrayList<>();
    long totalNonPinned = 0;

    if (remainingItemsNeeded > 0) {
      // Now get non-pinned PRs for the current page
      Specification<PullRequest> nonPinnedSpec =
          buildSpecification(pageRequest, currentUserId, false);

      // Create spring pageable for the non-pinned items
      Pageable pageable;
      if (pageRequest.getSortField() != null && !pageRequest.getSortField().isEmpty()) {
        Sort.Direction direction = "desc".equalsIgnoreCase(pageRequest.getSortDirection())
            ? Sort.Direction.DESC : Sort.Direction.ASC;
        pageable = PageRequest.of(
            offset / Math.max(1, remainingItemsNeeded),
            remainingItemsNeeded, Sort.by(direction, pageRequest.getSortField()));
      } else {
        // Default sort by updatedAt
        pageable = PageRequest.of(
            offset / Math.max(1, remainingItemsNeeded),
            remainingItemsNeeded, Sort.by(Sort.Direction.DESC, "updatedAt"));
      }

      // Get page of non-pinned PRs
      Page<PullRequest> nonPinnedPage = pullRequestRepository.findAll(nonPinnedSpec, pageable);
      totalNonPinned = nonPinnedPage.getTotalElements();

      log.debug("Found {} non-pinned PRs matching filter (total: {})",
          nonPinnedPage.getContent().size(), totalNonPinned);

      // Convert to DTOs
      nonPinnedDtos = nonPinnedPage.getContent().stream()
          .map(pr -> PullRequestBaseInfoDto.fromPullRequestAndUserPreference(pr, userPreference))
          .collect(Collectors.toList());

      log.debug("Found {} non-pinned PRs matching filter (total: {})",
          nonPinnedDtos.size(), nonPinnedPage.getTotalElements());
      if (!nonPinnedDtos.isEmpty()) {
        log.debug("Non-pinned PR IDs: {}",
            nonPinnedDtos.stream().map(PullRequestBaseInfoDto::id).collect(Collectors.toList()));
      }
    }

    // Combine pinned and non-pinned for the final result
    List<PullRequestBaseInfoDto> combinedContent = new ArrayList<>();

    // Add pinned items if we're on the first page
    if (pageRequest.getPage() == 0) {
      combinedContent.addAll(pinnedDtos);
    }

    // Add non-pinned items
    combinedContent.addAll(nonPinnedDtos);

    // Create the final response
    PageResponse<PullRequestBaseInfoDto> response = new PageResponse<>();
    response.setContent(combinedContent);
    response.setPage(pageRequest.getPage());
    response.setSize(pageRequest.getSize());
    response.setTotalElements(pinnedDtos.size() + totalNonPinned);
    response.setTotalPages(
        (int) Math.ceil((double) response.getTotalElements() / pageRequest.getSize()));

    log.debug("Returning {} combined PRs (page {}/{})",
        combinedContent.size(), response.getPage() + 1, response.getTotalPages());
    if (!combinedContent.isEmpty()) {
      log.debug("Combined PR IDs: {}",
          combinedContent.stream().map(PullRequestBaseInfoDto::id).collect(Collectors.toList()));
    }

    return response;
  }

  private Specification<PullRequest> buildSpecification(PullRequestPageRequest pageRequest,
                                                        String currentUserId,
                                                        boolean isPinned) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      // Add predicate for pinned status
      if (currentUserId != null) {
        // Create a subquery to find pinned PRs
        Subquery<Long> pinnedSubquery = query.subquery(Long.class);
        Root<UserPreference> userPrefRoot = pinnedSubquery.from(UserPreference.class);
        Join<UserPreference, User> userJoin = userPrefRoot.join("user");
        Join<UserPreference, PullRequest> prJoin = userPrefRoot.join("favouritePullRequests");

        pinnedSubquery.select(prJoin.get("id"))
            .where(cb.equal(userJoin.get("id"), Long.valueOf(currentUserId)));

        if (isPinned) {
          // For pinned PRs, require that they ARE in the favourites
          predicates.add(root.get("id").in(pinnedSubquery));
        } else {
          // For non-pinned, require that they are NOT in the favourites
          predicates.add(cb.not(root.get("id").in(pinnedSubquery)));
        }
      } else if (isPinned) {
        // If no user is logged in, there can't be any pinned PRs
        // Add an impossible condition to ensure empty result
        predicates.add(cb.equal(cb.literal(1), 0));
      }

      // Add search term predicate if provided
      if (pageRequest.getSearchTerm() != null && !pageRequest.getSearchTerm().trim().isEmpty()) {
        String searchTerm = "%" + pageRequest.getSearchTerm().toLowerCase() + "%";
        predicates.add(cb.or(
            cb.like(cb.lower(root.get("title")), searchTerm),
            cb.like(cb.toString(root.get("number")), searchTerm)
        ));
      }

      // Add filter type predicate
      switch (pageRequest.getFilterType()) {
        case OPEN:
          predicates.add(cb.equal(root.get("state"), Issue.State.OPEN));
          log.debug("Added OPEN filter");
          break;
        case OPEN_READY_FOR_REVIEW:
          predicates.add(cb.and(
              cb.equal(root.get("state"), Issue.State.OPEN),
              cb.equal(root.get("isDraft"), false)
          ));
          log.debug("Added OPEN_READY_FOR_REVIEW filter");
          break;
        case DRAFT:
          predicates.add(cb.and(
              cb.equal(root.get("state"), Issue.State.OPEN),
              cb.equal(root.get("isDraft"), true)
          ));
          log.debug("Added DRAFT filter");
          break;
        case MERGED:
          predicates.add(cb.and(
              cb.equal(root.get("state"), Issue.State.CLOSED),
              cb.equal(root.get("isMerged"), true)
          ));
          log.debug("Added MERGED filter");
          break;
        case CLOSED:
          predicates.add(cb.equal(root.get("state"), Issue.State.CLOSED));
          log.debug("Added CLOSED filter");
          break;
        case USER_AUTHORED:
          if (currentUserId != null) {
            Join<PullRequest, User> authorJoin = root.join("author", JoinType.INNER);
            predicates.add(cb.equal(authorJoin.get("id"), Long.valueOf(currentUserId)));
            log.debug("Added USER_AUTHORED filter with user ID: {}", currentUserId);
          }
          break;
        case ASSIGNED_TO_USER:
          if (currentUserId != null) {
            Join<PullRequest, User> assigneeJoin = root.join("assignees", JoinType.INNER);
            predicates.add(cb.equal(assigneeJoin.get("id"), Long.valueOf(currentUserId)));
            log.debug("Added ASSIGNED_TO_USER filter with user ID: {}", currentUserId);
          }
          break;
        case REVIEW_REQUESTED:
          if (currentUserId != null) {
            Join<PullRequest, User> reviewerJoin = root.join("requestedReviewers", JoinType.INNER);
            predicates.add(cb.equal(reviewerJoin.get("id"), Long.valueOf(currentUserId)));
            log.debug("Added REVIEW_REQUESTED filter with user ID: {}", currentUserId);
          }
          break;
        case ALL:
        default:
          // No additional predicates needed
          log.debug("Using ALL filter (no restrictions)");
          break;
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  private long countNonPinnedMatchingPRs(PullRequestPageRequest pageRequest,
                                         String currentUserId) {
    Specification<PullRequest> spec = buildSpecification(pageRequest, currentUserId, false);
    return pullRequestRepository.count(spec);
  }

  public Optional<PullRequestInfoDto> getPullRequestById(Long id) {
    return pullRequestRepository.findById(id).map(PullRequestInfoDto::fromPullRequest);
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
}
