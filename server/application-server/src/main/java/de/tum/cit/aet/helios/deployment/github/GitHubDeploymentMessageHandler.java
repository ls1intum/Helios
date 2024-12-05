    package de.tum.cit.aet.helios.deployment.github;

    import de.tum.cit.aet.helios.github.GitHubMessageHandler;
    import lombok.extern.log4j.Log4j2;
    import org.kohsuke.github.GHDeployment;
    import org.kohsuke.github.GHEvent;
    import org.kohsuke.github.GHEventPayload;
    import org.springframework.stereotype.Component;

    @Component
    @Log4j2
    public class GitHubDeploymentMessageHandler extends GitHubMessageHandler<GHEventPayload.Deployment> {

        private final GitHubDeploymentSyncService deploymentSyncService;

        private GitHubDeploymentMessageHandler(
                GitHubDeploymentSyncService deploymentSyncService) {
            super(GHEventPayload.Deployment.class);
            this.deploymentSyncService = deploymentSyncService;
        }

        @Override
        protected void handleEvent(GHEventPayload.Deployment eventPayload) {
            log.info("Received deployment event for repository: {}, deployment: {}, action: {}",
                    eventPayload.getRepository().getFullName(),
                    eventPayload.getDeployment().getId(),
                    eventPayload.getAction());

            eventPayload.getDeployment().getEnvironment();

            GHDeployment deployment = eventPayload.getDeployment();

            // not correct
            deploymentSyncService.syncDeploymentsOfRepository(eventPayload.getRepository());
        }

        @Override
        protected GHEvent getHandlerEvent() {
            return GHEvent.DEPLOYMENT;
        }
    }
