package de.tum.cit.aet.helios.issue.github;

import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.helios.github.GitHubMessageHandler;
import lombok.extern.log4j.Log4j2;

@Component
@Log4j2
public class GitHubIssueMessageHandler extends GitHubMessageHandler<GHEventPayload.Issue> {

    private GitHubIssueMessageHandler() {
        super(GHEventPayload.Issue.class);
    }

    @Override
    protected void handleEvent(GHEventPayload.Issue eventPayload) {
        var action = eventPayload.getAction();
        var repository = eventPayload.getRepository();
        var issue = eventPayload.getIssue();
        log.info("Received issue event for repository: {}, issue: {}, action: {}",
                repository.getFullName(),
                issue.getNumber(),
                action);    
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.ISSUES;
    }
}
