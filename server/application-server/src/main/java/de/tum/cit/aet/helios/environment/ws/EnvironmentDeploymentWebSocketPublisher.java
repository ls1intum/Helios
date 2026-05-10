package de.tum.cit.aet.helios.environment.ws;

import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
public class EnvironmentDeploymentWebSocketPublisher {

  private final EnvironmentDeploymentWebSocketHandler handler;

  public void publishAfterCommit(HeliosDeployment deployment) {
    if (deployment == null) {
      return;
    }
    publishAfterCommit(deployment.getEnvironment());
  }

  public void publishAfterCommit(Environment environment) {
    if (environment == null) {
      return;
    }
    GitRepository repository = environment.getRepository();
    if (repository == null || repository.getRepositoryId() == null || environment.getId() == null) {
      return;
    }
    publishAfterCommit(repository.getRepositoryId(), environment.getId());
  }

  public void publishAfterCommit(long repositoryId, long environmentId) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              handler.broadcastDeploymentInvalidated(repositoryId, environmentId);
            }
          });
      return;
    }

    handler.broadcastDeploymentInvalidated(repositoryId, environmentId);
  }
}
