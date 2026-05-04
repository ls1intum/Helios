package de.tum.cit.aet.helios.environment.ws;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class EnvironmentDeploymentWebSocketPublisherTest {

  @Mock private EnvironmentDeploymentWebSocketHandler handler;

  @InjectMocks private EnvironmentDeploymentWebSocketPublisher publisher;

  @AfterEach
  void tearDown() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void publishAfterCommitBroadcastsImmediatelyWithoutTransaction() {
    publisher.publishAfterCommit(42L, 7L);

    verify(handler).broadcastDeploymentInvalidated(42L, 7L);
  }

  @Test
  void publishAfterCommitWaitsForTransactionCommit() {
    TransactionSynchronizationManager.initSynchronization();

    publisher.publishAfterCommit(42L, 7L);

    verify(handler, never()).broadcastDeploymentInvalidated(42L, 7L);

    for (TransactionSynchronization synchronization :
        TransactionSynchronizationManager.getSynchronizations()) {
      synchronization.afterCommit();
    }

    verify(handler).broadcastDeploymentInvalidated(42L, 7L);
  }
}
