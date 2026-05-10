export type EnvironmentDeploymentWsClientMessage = { type: 'ping' };

export type EnvironmentDeploymentWsServerMessage =
  | {
      type: 'environment-deployment-invalidated';
      repositoryId: number;
      environmentId: number;
    }
  | { type: 'error'; code: string; message: string }
  | { type: 'pong' };
