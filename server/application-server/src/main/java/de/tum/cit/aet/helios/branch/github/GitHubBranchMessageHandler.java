package de.tum.cit.aet.helios.branch.github;

import java.io.IOException;

import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHRepository;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.helios.github.GitHubMessageHandler;
import de.tum.cit.aet.helios.gitrepo.github.GitHubRepositorySyncService;
import lombok.extern.log4j.Log4j2;

@Component
@Log4j2
public class GitHubBranchMessageHandler extends GitHubMessageHandler<GHEventPayload.Create> {

    // private final GitHubBranchSyncService branchSyncService;
    private final GitHubRepositorySyncService repositorySyncService;

    private GitHubBranchMessageHandler(
            // GitHubBranchSyncService branchSyncService,
            GitHubRepositorySyncService repositorySyncService) {
        super(GHEventPayload.Create.class);
        // this.branchSyncService = branchSyncService;
        this.repositorySyncService = repositorySyncService;
    }

    @Override
    protected void handleEvent(GHEventPayload.Create eventPayload) {
        String refType = eventPayload.getRefType();
        String ref = eventPayload.getRef();
        if ("branch".equals(refType)) {
            log.info("Received branch event for repository: {}, ref: {}, refType: {}, masterBranch {}, description: {} ",
                eventPayload.getRepository().getFullName(),                
                eventPayload.getRef(),
                eventPayload.getRefType(),
                eventPayload.getMasterBranch(),
                eventPayload.getDescription()
                );
            GHBranch branch;
            try {
                
                GHRepository repository = eventPayload.getRepository();
                branch = repository.getBranch(ref);
                log.info("Branch: {}", branch);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return;
        }
        repositorySyncService.processRepository(eventPayload.getRepository());
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.CREATE;
    }
}
