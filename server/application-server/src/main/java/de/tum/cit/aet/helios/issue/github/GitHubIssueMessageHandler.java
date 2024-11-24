package de.tum.cit.aet.helios.issue.github;

import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.helios.github.GitHubMessageHandler;

@Component
public class GitHubIssueMessageHandler extends GitHubMessageHandler<GHEventPayload.Issue> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubIssueMessageHandler.class);    

    private GitHubIssueMessageHandler() {
        super(GHEventPayload.Issue.class);
    }

    @Override
    protected void handleEvent(GHEventPayload.Issue eventPayload) {
        var action = eventPayload.getAction();
        var repository = eventPayload.getRepository();
        var issue = eventPayload.getIssue();
        logger.info("Received issue event for repository: {}, issue: {}, action: {}",
                repository.getFullName(),
                issue.getNumber(),
                action);    
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.ISSUES;
    }
}
