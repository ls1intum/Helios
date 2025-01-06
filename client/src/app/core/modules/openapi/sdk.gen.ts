// This file is auto-generated by @hey-api/openapi-ts

import { createClient, createConfig, type Options } from '@hey-api/client-fetch';
import type {
  UpdateWorkflowLabelData,
  UpdateWorkflowGroupsData,
  GetEnvironmentByIdData,
  GetEnvironmentByIdResponse,
  UpdateEnvironmentData,
  UpdateEnvironmentResponse,
  UnlockEnvironmentData,
  UnlockEnvironmentResponse,
  CreateWorkflowGroupData,
  CreateWorkflowGroupResponse,
  DeployToEnvironmentData,
  DeployToEnvironmentResponse,
  HealthCheckData,
  HealthCheckResponse,
  GetAllWorkflowsData,
  GetAllWorkflowsResponse,
  GetWorkflowByIdData,
  GetWorkflowByIdResponse,
  GetWorkflowsByStateData,
  GetWorkflowsByStateResponse,
  GetWorkflowsByRepositoryIdData,
  GetWorkflowsByRepositoryIdResponse,
  GetLatestWorkflowRunsByPullRequestIdAndHeadCommitData,
  GetLatestWorkflowRunsByPullRequestIdAndHeadCommitResponse,
  GetLatestWorkflowRunsByBranchAndHeadCommitData,
  GetLatestWorkflowRunsByBranchAndHeadCommitResponse,
  GetGroupsWithWorkflowsData,
  GetGroupsWithWorkflowsResponse,
  GetRepositoryByIdData,
  GetRepositoryByIdResponse,
  GetAllPullRequestsData,
  GetAllPullRequestsResponse,
  GetPullRequestByIdData,
  GetPullRequestByIdResponse,
  GetPullRequestByRepositoryIdAndNumberData,
  GetPullRequestByRepositoryIdAndNumberResponse,
  GetPullRequestByRepositoryIdData,
  GetPullRequestByRepositoryIdResponse,
  GetAllEnvironmentsData,
  GetAllEnvironmentsResponse,
  GetEnvironmentsByUserLockingData,
  GetEnvironmentsByUserLockingResponse,
  GetEnvironmentsByRepositoryIdData,
  GetEnvironmentsByRepositoryIdResponse,
  GetAllDeploymentsData,
  GetAllDeploymentsResponse,
  GetDeploymentByIdData,
  GetDeploymentByIdResponse,
  GetDeploymentsByEnvironmentIdData,
  GetDeploymentsByEnvironmentIdResponse,
  GetLatestDeploymentByEnvironmentIdData,
  GetLatestDeploymentByEnvironmentIdResponse,
  GetCommitByRepositoryIdAndNameData,
  GetCommitByRepositoryIdAndNameResponse,
  GetAllBranchesData,
  GetAllBranchesResponse,
  GetBranchByRepositoryIdAndNameData,
  GetBranchByRepositoryIdAndNameResponse,
  DeleteWorkflowGroupData,
} from './types.gen';

export const client = createClient(createConfig());

export const updateWorkflowLabel = <ThrowOnError extends boolean = false>(options: Options<UpdateWorkflowLabelData, ThrowOnError>) => {
  return (options?.client ?? client).put<unknown, unknown, ThrowOnError>({
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
    url: '/api/workflows/{workflowId}/label',
  });
};

export const updateWorkflowGroups = <ThrowOnError extends boolean = false>(options: Options<UpdateWorkflowGroupsData, ThrowOnError>) => {
  return (options?.client ?? client).put<unknown, unknown, ThrowOnError>({
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
    url: '/api/settings/{repositoryId}/groups/update',
  });
};

export const getEnvironmentById = <ThrowOnError extends boolean = false>(options: Options<GetEnvironmentByIdData, ThrowOnError>) => {
  return (options?.client ?? client).get<GetEnvironmentByIdResponse, unknown, ThrowOnError>({
    ...options,
    url: '/api/environments/{id}',
  });
};

export const updateEnvironment = <ThrowOnError extends boolean = false>(options: Options<UpdateEnvironmentData, ThrowOnError>) => {
  return (options?.client ?? client).put<UpdateEnvironmentResponse, unknown, ThrowOnError>({
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
    url: '/api/environments/{id}',
  });
};

export const unlockEnvironment = <ThrowOnError extends boolean = false>(options: Options<UnlockEnvironmentData, ThrowOnError>) => {
  return (options?.client ?? client).put<UnlockEnvironmentResponse, unknown, ThrowOnError>({
    ...options,
    url: '/api/environments/{id}/unlock',
  });
};

export const createWorkflowGroup = <ThrowOnError extends boolean = false>(options: Options<CreateWorkflowGroupData, ThrowOnError>) => {
  return (options?.client ?? client).post<CreateWorkflowGroupResponse, unknown, ThrowOnError>({
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
    url: '/api/settings/{repositoryId}/groups/create',
  });
};

export const deployToEnvironment = <ThrowOnError extends boolean = false>(options: Options<DeployToEnvironmentData, ThrowOnError>) => {
  return (options?.client ?? client).post<DeployToEnvironmentResponse, unknown, ThrowOnError>({
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
    url: '/api/deployments/deploy',
  });
};

export const healthCheck = <ThrowOnError extends boolean = false>(options?: Options<HealthCheckData, ThrowOnError>) => {
  return (options?.client ?? client).get<HealthCheckResponse, unknown, ThrowOnError>({
    ...options,
    url: '/status/health',
  });
};

export const getAllWorkflows = <ThrowOnError extends boolean = false>(options?: Options<GetAllWorkflowsData, ThrowOnError>) => {
  return (options?.client ?? client).get<GetAllWorkflowsResponse, unknown, ThrowOnError>({
    ...options,
    url: '/api/workflows',
  });
};

export const getWorkflowById = <ThrowOnError extends boolean = false>(options: Options<GetWorkflowByIdData, ThrowOnError>) => {
  return (options?.client ?? client).get<GetWorkflowByIdResponse, unknown, ThrowOnError>({
    ...options,
    url: '/api/workflows/{id}',
  });
};

export const getWorkflowsByState = <ThrowOnError extends boolean = false>(options: Options<GetWorkflowsByStateData, ThrowOnError>) => {
  return (options?.client ?? client).get<GetWorkflowsByStateResponse, unknown, ThrowOnError>({
    ...options,
    url: '/api/workflows/state/{state}',
  });
};

export const getWorkflowsByRepositoryId = <ThrowOnError extends boolean = false>(options: Options<GetWorkflowsByRepositoryIdData, ThrowOnError>) => {
  return (options?.client ?? client).get<GetWorkflowsByRepositoryIdResponse, unknown, ThrowOnError>({
    ...options,
    url: '/api/workflows/repository/{repositoryId}',
  });
};

export const getLatestWorkflowRunsByPullRequestIdAndHeadCommit = <ThrowOnError extends boolean = false>(
  options: Options<GetLatestWorkflowRunsByPullRequestIdAndHeadCommitData, ThrowOnError>
) => {
  return (options?.client ?? client).get<GetLatestWorkflowRunsByPullRequestIdAndHeadCommitResponse, unknown, ThrowOnError>({
    ...options,
    url: '/api/workflows/pr/{pullRequestId}',
  });
};

export const getLatestWorkflowRunsByBranchAndHeadCommit = <ThrowOnError extends boolean = false>(
  options: Options<GetLatestWorkflowRunsByBranchAndHeadCommitData, ThrowOnError>
) => {
  return (options?.client ?? client).get<GetLatestWorkflowRunsByBranchAndHeadCommitResponse, unknown, ThrowOnError>({
    ...options,
    url: '/api/workflows/branch/{branch}',
  });
};

export const getGroupsWithWorkflows = <ThrowOnError extends boolean = false>(options: Options<GetGroupsWithWorkflowsData, ThrowOnError>) => {
  return (options?.client ?? client).get<GetGroupsWithWorkflowsResponse, unknown, ThrowOnError>({
    ...options,
    url: '/api/settings/{repositoryId}/groups',
  });
};

export const getRepositoryById = <ThrowOnError extends boolean = false>(options: Options<GetRepositoryByIdData, ThrowOnError>) => {
  return (options?.client ?? client).get<GetRepositoryByIdResponse, unknown, ThrowOnError>({
    ...options,
    url: '/api/repository/{id}',
  });
};

export const getAllPullRequests = <ThrowOnError extends boolean = false>(options?: Options<GetAllPullRequestsData, ThrowOnError>) => {
  return (options?.client ?? client).get<GetAllPullRequestsResponse, unknown, ThrowOnError>({
    ...options,
    url: '/api/pullrequests',
  });
};

export const getPullRequestById = <ThrowOnError extends boolean = false>(options: Options<GetPullRequestByIdData, ThrowOnError>) => {
  return (options?.client ?? client).get<GetPullRequestByIdResponse, unknown, ThrowOnError>({
    ...options,
    url: '/api/pullrequests/{id}',
  });
};

export const getPullRequestByRepositoryIdAndNumber = <ThrowOnError extends boolean = false>(options: Options<GetPullRequestByRepositoryIdAndNumberData, ThrowOnError>) => {
  return (options?.client ?? client).get<GetPullRequestByRepositoryIdAndNumberResponse, unknown, ThrowOnError>({
    ...options,
    url: '/api/pullrequests/repository/{repoId}/pr/{number}',
  });
};

export const getPullRequestByRepositoryId = <ThrowOnError extends boolean = false>(options: Options<GetPullRequestByRepositoryIdData, ThrowOnError>) => {
  return (options?.client ?? client).get<GetPullRequestByRepositoryIdResponse, unknown, ThrowOnError>({
    ...options,
    url: '/api/pullrequests/repository/{id}',
  });
};

export const getAllEnvironments = <ThrowOnError extends boolean = false>(options?: Options<GetAllEnvironmentsData, ThrowOnError>) => {
  return (options?.client ?? client).get<GetAllEnvironmentsResponse, unknown, ThrowOnError>({
    ...options,
    url: '/api/environments',
  });
};

export const getEnvironmentsByUserLocking = <ThrowOnError extends boolean = false>(options?: Options<GetEnvironmentsByUserLockingData, ThrowOnError>) => {
  return (options?.client ?? client).get<GetEnvironmentsByUserLockingResponse, unknown, ThrowOnError>({
    ...options,
    url: '/api/environments/user_locking',
  });
};

export const getEnvironmentsByRepositoryId = <ThrowOnError extends boolean = false>(options: Options<GetEnvironmentsByRepositoryIdData, ThrowOnError>) => {
  return (options?.client ?? client).get<GetEnvironmentsByRepositoryIdResponse, unknown, ThrowOnError>({
    ...options,
    url: '/api/environments/repository/{repositoryId}',
  });
};

export const getAllDeployments = <ThrowOnError extends boolean = false>(options?: Options<GetAllDeploymentsData, ThrowOnError>) => {
  return (options?.client ?? client).get<GetAllDeploymentsResponse, unknown, ThrowOnError>({
    ...options,
    url: '/api/deployments',
  });
};

export const getDeploymentById = <ThrowOnError extends boolean = false>(options: Options<GetDeploymentByIdData, ThrowOnError>) => {
  return (options?.client ?? client).get<GetDeploymentByIdResponse, unknown, ThrowOnError>({
    ...options,
    url: '/api/deployments/{id}',
  });
};

export const getDeploymentsByEnvironmentId = <ThrowOnError extends boolean = false>(options: Options<GetDeploymentsByEnvironmentIdData, ThrowOnError>) => {
  return (options?.client ?? client).get<GetDeploymentsByEnvironmentIdResponse, unknown, ThrowOnError>({
    ...options,
    url: '/api/deployments/environment/{environmentId}',
  });
};

export const getLatestDeploymentByEnvironmentId = <ThrowOnError extends boolean = false>(options: Options<GetLatestDeploymentByEnvironmentIdData, ThrowOnError>) => {
  return (options?.client ?? client).get<GetLatestDeploymentByEnvironmentIdResponse, unknown, ThrowOnError>({
    ...options,
    url: '/api/deployments/environment/{environmentId}/latest',
  });
};

export const getCommitByRepositoryIdAndName = <ThrowOnError extends boolean = false>(options: Options<GetCommitByRepositoryIdAndNameData, ThrowOnError>) => {
  return (options?.client ?? client).get<GetCommitByRepositoryIdAndNameResponse, unknown, ThrowOnError>({
    ...options,
    url: '/api/commits/repository/{repoId}/commit/{sha}',
  });
};

export const getAllBranches = <ThrowOnError extends boolean = false>(options?: Options<GetAllBranchesData, ThrowOnError>) => {
  return (options?.client ?? client).get<GetAllBranchesResponse, unknown, ThrowOnError>({
    ...options,
    url: '/api/branches',
  });
};

export const getBranchByRepositoryIdAndName = <ThrowOnError extends boolean = false>(options: Options<GetBranchByRepositoryIdAndNameData, ThrowOnError>) => {
  return (options?.client ?? client).get<GetBranchByRepositoryIdAndNameResponse, unknown, ThrowOnError>({
    ...options,
    url: '/api/branches/repository/{repoId}/branch',
  });
};

export const deleteWorkflowGroup = <ThrowOnError extends boolean = false>(options: Options<DeleteWorkflowGroupData, ThrowOnError>) => {
  return (options?.client ?? client).delete<unknown, unknown, ThrowOnError>({
    ...options,
    url: '/api/settings/{repositoryId}/groups/{groupId}',
  });
};
