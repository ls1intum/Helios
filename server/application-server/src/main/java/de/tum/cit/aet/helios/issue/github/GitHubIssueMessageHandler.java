package de.tum.cit.aet.helios.issue.github;

import de.tum.cit.aet.helios.github.GitHubMessageHandler;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class GitHubIssueMessageHandler extends GitHubMessageHandler<GHEventPayload.Issue> {
  @Override
  protected Class<GHEventPayload.Issue> getPayloadClass() {
    return GHEventPayload.Issue.class;
  }

  @Override
  protected GHEvent getPayloadType() {
    return GHEvent.ISSUES;
  }

  @Override
  protected void handleInstalledRepositoryEvent(GHEventPayload.Issue eventPayload) {
    var action = eventPayload.getAction();
    var repository = eventPayload.getRepository();
    var issue = eventPayload.getIssue();
    log.info(
        "Received issue event for repository: {}, issue: {}, action: {}",
        repository.getFullName(),
        issue.getNumber(),
        action);
  }
}
