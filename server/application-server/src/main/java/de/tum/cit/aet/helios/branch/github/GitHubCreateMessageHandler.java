package de.tum.cit.aet.helios.branch.github;

import java.io.IOException;

import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHEvent;
import org.kohsuke.github.GHEventPayload;
import org.kohsuke.github.GHRepository;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.helios.github.GitHubMessageHandler;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.github.GitHubRepositorySyncService;
import lombok.extern.log4j.Log4j2;

@Component
@Log4j2
public class GitHubCreateMessageHandler extends GitHubMessageHandler<GHEventPayload.Create> {

    private final GitHubBranchSyncService branchSyncService;
    private final GitHubRepositorySyncService repositorySyncService;
    private final GitHubService gitHubService;

    private GitHubCreateMessageHandler(
            GitHubBranchSyncService branchSyncService,
            GitHubRepositorySyncService repositorySyncService,
            GitHubService gitHubService) {
        super(GHEventPayload.Create.class);
        this.branchSyncService = branchSyncService;
        this.repositorySyncService = repositorySyncService;
        this.gitHubService = gitHubService;
    }

    @Override
    protected void handleEvent(GHEventPayload.Create eventPayload) {
        String refType = eventPayload.getRefType();
        String ref = eventPayload.getRef();
        GHRepository repository;
        GHBranch branch;
        if ("branch".equals(refType)) {
            log.info("Received branch event for repository: {}, ref: {}, refType: {}, masterBranch {}, description: {} ",
                eventPayload.getRepository().getFullName(),                
                eventPayload.getRef(),
                eventPayload.getRefType(),
                eventPayload.getMasterBranch(),
                eventPayload.getDescription()
                );
            
            try {                
                repository = eventPayload.getRepository();
                var curRepo = gitHubService.getRepository(repository.getFullName());               
                branch = curRepo.getBranch(ref);
            
                repositorySyncService.processRepository(eventPayload.getRepository());
                branchSyncService.processBranch(branch, repository);
            } catch (IOException e) {            
                e.printStackTrace();
            }
            return;
        }
    }

    @Override
    protected GHEvent getHandlerEvent() {
        return GHEvent.CREATE;
    }
}
