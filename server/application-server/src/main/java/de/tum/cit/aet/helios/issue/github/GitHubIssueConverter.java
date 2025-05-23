package de.tum.cit.aet.helios.issue.github;

import de.tum.cit.aet.helios.github.BaseGitServiceEntityConverter;
import de.tum.cit.aet.helios.issue.Issue;
import de.tum.cit.aet.helios.util.DateUtil;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class GitHubIssueConverter extends BaseGitServiceEntityConverter<GHIssue, Issue> {

  @Override
  public Issue convert(@NonNull GHIssue source) {
    return update(source, new Issue());
  }

  @Override
  public Issue update(@NonNull GHIssue source, @NonNull Issue issue) {
    convertBaseFields(source, issue);
    issue.setNumber(source.getNumber());
    issue.setState(convertState(source.getState()));
    issue.setTitle(source.getTitle());
    issue.setBody(source.getBody());
    issue.setHtmlUrl(source.getHtmlUrl().toString());
    issue.setLocked(source.isLocked());
    issue.setClosedAt(DateUtil.convertToOffsetDateTime(source.getClosedAt()));
    issue.setCommentsCount(issue.getCommentsCount());
    return issue;
  }

  private Issue.State convertState(GHIssueState state) {
    switch (state) {
      case OPEN:
        return Issue.State.OPEN;
      case CLOSED:
        return Issue.State.CLOSED;
      default:
        return Issue.State.CLOSED;
    }
  }
}
