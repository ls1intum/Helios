// This file is auto-generated by @hey-api/openapi-ts

import type { Options as ClientOptions, TDataShape, Client } from '@hey-api/client-fetch';
import type {
  UpdateWorkflowLabelData,
  GetGitRepoSettingsData,
  GetGitRepoSettingsResponse,
  UpdateGitRepoSettingsData,
  UpdateGitRepoSettingsResponse,
  UpdateWorkflowGroupsData,
  GetEnvironmentByIdData,
  GetEnvironmentByIdResponse,
  UpdateEnvironmentData,
  UpdateEnvironmentResponse,
  UnlockEnvironmentData,
  UnlockEnvironmentResponse,
  LockEnvironmentData,
  LockEnvironmentResponse,
  ExtendEnvironmentLockData,
  ExtendEnvironmentLockResponse,
  SyncWorkflowsByRepositoryIdData,
  CreateWorkflowGroupData,
  CreateWorkflowGroupResponse,
  GetAllReleaseCandidatesData,
  GetAllReleaseCandidatesResponse,
  CreateReleaseCandidateData,
  CreateReleaseCandidateResponse,
  EvaluateData,
  SetPrPinnedByNumberData,
  DeployToEnvironmentData,
  DeployToEnvironmentResponse,
  SetBranchPinnedByRepositoryIdAndNameAndUserIdData,
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
  GetUserPermissionsData,
  GetUserPermissionsResponse,
  GetLatestTestResultsByPullRequestIdData,
  GetLatestTestResultsByPullRequestIdResponse,
  GetLatestGroupedTestResultsByPullRequestIdData,
  GetLatestGroupedTestResultsByPullRequestIdResponse,
  GetLatestGroupedTestResultsByBranchData,
  GetLatestGroupedTestResultsByBranchResponse,
  GetLatestTestResultsByBranchData,
  GetLatestTestResultsByBranchResponse,
  GetGroupsWithWorkflowsData,
  GetGroupsWithWorkflowsResponse,
  GetAllRepositoriesData,
  GetAllRepositoriesResponse,
  GetRepositoryByIdData,
  GetRepositoryByIdResponse,
  DeleteReleaseCandidateByNameData,
  DeleteReleaseCandidateByNameResponse,
  GetReleaseCandidateByNameData,
  GetReleaseCandidateByNameResponse,
  GetCommitsSinceLastReleaseCandidateData,
  GetCommitsSinceLastReleaseCandidateResponse,
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
  GetLockHistoryByEnvironmentIdData,
  GetLockHistoryByEnvironmentIdResponse,
  GetAllEnabledEnvironmentsData,
  GetAllEnabledEnvironmentsResponse,
  GetAllDeploymentsData,
  GetAllDeploymentsResponse,
  GetDeploymentByIdData,
  GetDeploymentByIdResponse,
  GetDeploymentsByEnvironmentIdData,
  GetDeploymentsByEnvironmentIdResponse,
  GetLatestDeploymentByEnvironmentIdData,
  GetLatestDeploymentByEnvironmentIdResponse,
  GetActivityHistoryByEnvironmentIdData,
  GetActivityHistoryByEnvironmentIdResponse,
  GetCommitByRepositoryIdAndNameData,
  GetCommitByRepositoryIdAndNameResponse,
  GetAllBranchesData,
  GetAllBranchesResponse,
  GetBranchByRepositoryIdAndNameData,
  GetBranchByRepositoryIdAndNameResponse,
  DeleteWorkflowGroupData,
} from './types.gen';
import { client as _heyApiClient } from './client.gen';

export type Options<TData extends TDataShape = TDataShape, ThrowOnError extends boolean = boolean> = ClientOptions<TData, ThrowOnError> & {
  /**
   * You can provide a client instance returned by `createClient()` instead of
   * individual options. This might be also useful if you want to implement a
   * custom client.
   */
  client?: Client;
  /**
   * You can pass arbitrary values through the `meta` object. This can be
   * used to access values that aren't defined as part of the SDK function.
   */
  meta?: Record<string, unknown>;
};

export const updateWorkflowLabel = <ThrowOnError extends boolean = false>(options: Options<UpdateWorkflowLabelData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).put<unknown, unknown, ThrowOnError>({
    url: '/api/workflows/{workflowId}/label',
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
  });
};

export const getGitRepoSettings = <ThrowOnError extends boolean = false>(options: Options<GetGitRepoSettingsData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).get<GetGitRepoSettingsResponse, unknown, ThrowOnError>({
    url: '/api/settings/{repositoryId}/settings',
    ...options,
  });
};

export const updateGitRepoSettings = <ThrowOnError extends boolean = false>(options: Options<UpdateGitRepoSettingsData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).put<UpdateGitRepoSettingsResponse, unknown, ThrowOnError>({
    url: '/api/settings/{repositoryId}/settings',
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
  });
};

export const updateWorkflowGroups = <ThrowOnError extends boolean = false>(options: Options<UpdateWorkflowGroupsData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).put<unknown, unknown, ThrowOnError>({
    url: '/api/settings/{repositoryId}/groups/update',
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
  });
};

export const getEnvironmentById = <ThrowOnError extends boolean = false>(options: Options<GetEnvironmentByIdData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).get<GetEnvironmentByIdResponse, unknown, ThrowOnError>({
    url: '/api/environments/{id}',
    ...options,
  });
};

export const updateEnvironment = <ThrowOnError extends boolean = false>(options: Options<UpdateEnvironmentData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).put<UpdateEnvironmentResponse, unknown, ThrowOnError>({
    url: '/api/environments/{id}',
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
  });
};

export const unlockEnvironment = <ThrowOnError extends boolean = false>(options: Options<UnlockEnvironmentData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).put<UnlockEnvironmentResponse, unknown, ThrowOnError>({
    url: '/api/environments/{id}/unlock',
    ...options,
  });
};

export const lockEnvironment = <ThrowOnError extends boolean = false>(options: Options<LockEnvironmentData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).put<LockEnvironmentResponse, unknown, ThrowOnError>({
    url: '/api/environments/{id}/lock',
    ...options,
  });
};

export const extendEnvironmentLock = <ThrowOnError extends boolean = false>(options: Options<ExtendEnvironmentLockData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).put<ExtendEnvironmentLockResponse, unknown, ThrowOnError>({
    url: '/api/environments/{id}/extend-lock',
    ...options,
  });
};

export const syncWorkflowsByRepositoryId = <ThrowOnError extends boolean = false>(options: Options<SyncWorkflowsByRepositoryIdData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).post<unknown, unknown, ThrowOnError>({
    url: '/api/workflows/repository/{repositoryId}/sync',
    ...options,
  });
};

export const createWorkflowGroup = <ThrowOnError extends boolean = false>(options: Options<CreateWorkflowGroupData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).post<CreateWorkflowGroupResponse, unknown, ThrowOnError>({
    url: '/api/settings/{repositoryId}/groups/create',
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
  });
};

export const getAllReleaseCandidates = <ThrowOnError extends boolean = false>(options?: Options<GetAllReleaseCandidatesData, ThrowOnError>) => {
  return (options?.client ?? _heyApiClient).get<GetAllReleaseCandidatesResponse, unknown, ThrowOnError>({
    url: '/api/release-candidate',
    ...options,
  });
};

export const createReleaseCandidate = <ThrowOnError extends boolean = false>(options: Options<CreateReleaseCandidateData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).post<CreateReleaseCandidateResponse, unknown, ThrowOnError>({
    url: '/api/release-candidate',
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
  });
};

export const evaluate = <ThrowOnError extends boolean = false>(options: Options<EvaluateData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).post<unknown, unknown, ThrowOnError>({
    url: '/api/release-candidate/{name}/evaluate/{isWorking}',
    ...options,
  });
};

export const setPrPinnedByNumber = <ThrowOnError extends boolean = false>(options: Options<SetPrPinnedByNumberData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).post<unknown, unknown, ThrowOnError>({
    url: '/api/pullrequests/{pr}/pin',
    ...options,
  });
};

export const deployToEnvironment = <ThrowOnError extends boolean = false>(options: Options<DeployToEnvironmentData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).post<DeployToEnvironmentResponse, unknown, ThrowOnError>({
    url: '/api/deployments/deploy',
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
  });
};

export const setBranchPinnedByRepositoryIdAndNameAndUserId = <ThrowOnError extends boolean = false>(
  options: Options<SetBranchPinnedByRepositoryIdAndNameAndUserIdData, ThrowOnError>
) => {
  return (options.client ?? _heyApiClient).post<unknown, unknown, ThrowOnError>({
    url: '/api/branches/repository/{repoId}/pin',
    ...options,
  });
};

export const healthCheck = <ThrowOnError extends boolean = false>(options?: Options<HealthCheckData, ThrowOnError>) => {
  return (options?.client ?? _heyApiClient).get<HealthCheckResponse, unknown, ThrowOnError>({
    url: '/status/health',
    ...options,
  });
};

export const getAllWorkflows = <ThrowOnError extends boolean = false>(options?: Options<GetAllWorkflowsData, ThrowOnError>) => {
  return (options?.client ?? _heyApiClient).get<GetAllWorkflowsResponse, unknown, ThrowOnError>({
    url: '/api/workflows',
    ...options,
  });
};

export const getWorkflowById = <ThrowOnError extends boolean = false>(options: Options<GetWorkflowByIdData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).get<GetWorkflowByIdResponse, unknown, ThrowOnError>({
    url: '/api/workflows/{id}',
    ...options,
  });
};

export const getWorkflowsByState = <ThrowOnError extends boolean = false>(options: Options<GetWorkflowsByStateData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).get<GetWorkflowsByStateResponse, unknown, ThrowOnError>({
    url: '/api/workflows/state/{state}',
    ...options,
  });
};

export const getWorkflowsByRepositoryId = <ThrowOnError extends boolean = false>(options: Options<GetWorkflowsByRepositoryIdData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).get<GetWorkflowsByRepositoryIdResponse, unknown, ThrowOnError>({
    url: '/api/workflows/repository/{repositoryId}',
    ...options,
  });
};

export const getLatestWorkflowRunsByPullRequestIdAndHeadCommit = <ThrowOnError extends boolean = false>(
  options: Options<GetLatestWorkflowRunsByPullRequestIdAndHeadCommitData, ThrowOnError>
) => {
  return (options.client ?? _heyApiClient).get<GetLatestWorkflowRunsByPullRequestIdAndHeadCommitResponse, unknown, ThrowOnError>({
    url: '/api/workflows/pr/{pullRequestId}',
    ...options,
  });
};

export const getLatestWorkflowRunsByBranchAndHeadCommit = <ThrowOnError extends boolean = false>(
  options: Options<GetLatestWorkflowRunsByBranchAndHeadCommitData, ThrowOnError>
) => {
  return (options.client ?? _heyApiClient).get<GetLatestWorkflowRunsByBranchAndHeadCommitResponse, unknown, ThrowOnError>({
    url: '/api/workflows/branch',
    ...options,
  });
};

export const getUserPermissions = <ThrowOnError extends boolean = false>(options?: Options<GetUserPermissionsData, ThrowOnError>) => {
  return (options?.client ?? _heyApiClient).get<GetUserPermissionsResponse, unknown, ThrowOnError>({
    url: '/api/user-permissions',
    ...options,
  });
};

export const getLatestTestResultsByPullRequestId = <ThrowOnError extends boolean = false>(options: Options<GetLatestTestResultsByPullRequestIdData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).get<GetLatestTestResultsByPullRequestIdResponse, unknown, ThrowOnError>({
    url: '/api/tests/pr/{pullRequestId}',
    ...options,
  });
};

export const getLatestGroupedTestResultsByPullRequestId = <ThrowOnError extends boolean = false>(
  options: Options<GetLatestGroupedTestResultsByPullRequestIdData, ThrowOnError>
) => {
  return (options.client ?? _heyApiClient).get<GetLatestGroupedTestResultsByPullRequestIdResponse, unknown, ThrowOnError>({
    url: '/api/tests/grouped/pr/{pullRequestId}',
    ...options,
  });
};

export const getLatestGroupedTestResultsByBranch = <ThrowOnError extends boolean = false>(options: Options<GetLatestGroupedTestResultsByBranchData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).get<GetLatestGroupedTestResultsByBranchResponse, unknown, ThrowOnError>({
    url: '/api/tests/grouped/branch',
    ...options,
  });
};

export const getLatestTestResultsByBranch = <ThrowOnError extends boolean = false>(options: Options<GetLatestTestResultsByBranchData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).get<GetLatestTestResultsByBranchResponse, unknown, ThrowOnError>({
    url: '/api/tests/branch',
    ...options,
  });
};

export const getGroupsWithWorkflows = <ThrowOnError extends boolean = false>(options: Options<GetGroupsWithWorkflowsData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).get<GetGroupsWithWorkflowsResponse, unknown, ThrowOnError>({
    url: '/api/settings/{repositoryId}/groups',
    ...options,
  });
};

export const getAllRepositories = <ThrowOnError extends boolean = false>(options?: Options<GetAllRepositoriesData, ThrowOnError>) => {
  return (options?.client ?? _heyApiClient).get<GetAllRepositoriesResponse, unknown, ThrowOnError>({
    url: '/api/repository',
    ...options,
  });
};

export const getRepositoryById = <ThrowOnError extends boolean = false>(options: Options<GetRepositoryByIdData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).get<GetRepositoryByIdResponse, unknown, ThrowOnError>({
    url: '/api/repository/{id}',
    ...options,
  });
};

export const deleteReleaseCandidateByName = <ThrowOnError extends boolean = false>(options: Options<DeleteReleaseCandidateByNameData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).delete<DeleteReleaseCandidateByNameResponse, unknown, ThrowOnError>({
    url: '/api/release-candidate/{name}',
    ...options,
  });
};

export const getReleaseCandidateByName = <ThrowOnError extends boolean = false>(options: Options<GetReleaseCandidateByNameData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).get<GetReleaseCandidateByNameResponse, unknown, ThrowOnError>({
    url: '/api/release-candidate/{name}',
    ...options,
  });
};

export const getCommitsSinceLastReleaseCandidate = <ThrowOnError extends boolean = false>(options: Options<GetCommitsSinceLastReleaseCandidateData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).get<GetCommitsSinceLastReleaseCandidateResponse, unknown, ThrowOnError>({
    url: '/api/release-candidate/newcommits',
    ...options,
  });
};

export const getAllPullRequests = <ThrowOnError extends boolean = false>(options?: Options<GetAllPullRequestsData, ThrowOnError>) => {
  return (options?.client ?? _heyApiClient).get<GetAllPullRequestsResponse, unknown, ThrowOnError>({
    url: '/api/pullrequests',
    ...options,
  });
};

export const getPullRequestById = <ThrowOnError extends boolean = false>(options: Options<GetPullRequestByIdData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).get<GetPullRequestByIdResponse, unknown, ThrowOnError>({
    url: '/api/pullrequests/{id}',
    ...options,
  });
};

export const getPullRequestByRepositoryIdAndNumber = <ThrowOnError extends boolean = false>(options: Options<GetPullRequestByRepositoryIdAndNumberData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).get<GetPullRequestByRepositoryIdAndNumberResponse, unknown, ThrowOnError>({
    url: '/api/pullrequests/repository/{repoId}/pr/{number}',
    ...options,
  });
};

export const getPullRequestByRepositoryId = <ThrowOnError extends boolean = false>(options: Options<GetPullRequestByRepositoryIdData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).get<GetPullRequestByRepositoryIdResponse, unknown, ThrowOnError>({
    url: '/api/pullrequests/repository/{id}',
    ...options,
  });
};

export const getAllEnvironments = <ThrowOnError extends boolean = false>(options?: Options<GetAllEnvironmentsData, ThrowOnError>) => {
  return (options?.client ?? _heyApiClient).get<GetAllEnvironmentsResponse, unknown, ThrowOnError>({
    url: '/api/environments',
    ...options,
  });
};

export const getEnvironmentsByUserLocking = <ThrowOnError extends boolean = false>(options?: Options<GetEnvironmentsByUserLockingData, ThrowOnError>) => {
  return (options?.client ?? _heyApiClient).get<GetEnvironmentsByUserLockingResponse, unknown, ThrowOnError>({
    url: '/api/environments/userLocking',
    ...options,
  });
};

export const getEnvironmentsByRepositoryId = <ThrowOnError extends boolean = false>(options: Options<GetEnvironmentsByRepositoryIdData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).get<GetEnvironmentsByRepositoryIdResponse, unknown, ThrowOnError>({
    url: '/api/environments/repository/{repositoryId}',
    ...options,
  });
};

export const getLockHistoryByEnvironmentId = <ThrowOnError extends boolean = false>(options: Options<GetLockHistoryByEnvironmentIdData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).get<GetLockHistoryByEnvironmentIdResponse, unknown, ThrowOnError>({
    url: '/api/environments/environment/{environmentId}/lockHistory',
    ...options,
  });
};

export const getAllEnabledEnvironments = <ThrowOnError extends boolean = false>(options?: Options<GetAllEnabledEnvironmentsData, ThrowOnError>) => {
  return (options?.client ?? _heyApiClient).get<GetAllEnabledEnvironmentsResponse, unknown, ThrowOnError>({
    url: '/api/environments/enabled',
    ...options,
  });
};

export const getAllDeployments = <ThrowOnError extends boolean = false>(options?: Options<GetAllDeploymentsData, ThrowOnError>) => {
  return (options?.client ?? _heyApiClient).get<GetAllDeploymentsResponse, unknown, ThrowOnError>({
    url: '/api/deployments',
    ...options,
  });
};

export const getDeploymentById = <ThrowOnError extends boolean = false>(options: Options<GetDeploymentByIdData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).get<GetDeploymentByIdResponse, unknown, ThrowOnError>({
    url: '/api/deployments/{id}',
    ...options,
  });
};

export const getDeploymentsByEnvironmentId = <ThrowOnError extends boolean = false>(options: Options<GetDeploymentsByEnvironmentIdData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).get<GetDeploymentsByEnvironmentIdResponse, unknown, ThrowOnError>({
    url: '/api/deployments/environment/{environmentId}',
    ...options,
  });
};

export const getLatestDeploymentByEnvironmentId = <ThrowOnError extends boolean = false>(options: Options<GetLatestDeploymentByEnvironmentIdData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).get<GetLatestDeploymentByEnvironmentIdResponse, unknown, ThrowOnError>({
    url: '/api/deployments/environment/{environmentId}/latest',
    ...options,
  });
};

export const getActivityHistoryByEnvironmentId = <ThrowOnError extends boolean = false>(options: Options<GetActivityHistoryByEnvironmentIdData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).get<GetActivityHistoryByEnvironmentIdResponse, unknown, ThrowOnError>({
    url: '/api/deployments/environment/{environmentId}/activity-history',
    ...options,
  });
};

export const getCommitByRepositoryIdAndName = <ThrowOnError extends boolean = false>(options: Options<GetCommitByRepositoryIdAndNameData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).get<GetCommitByRepositoryIdAndNameResponse, unknown, ThrowOnError>({
    url: '/api/commits/repository/{repoId}/commit/{sha}',
    ...options,
  });
};

export const getAllBranches = <ThrowOnError extends boolean = false>(options?: Options<GetAllBranchesData, ThrowOnError>) => {
  return (options?.client ?? _heyApiClient).get<GetAllBranchesResponse, unknown, ThrowOnError>({
    url: '/api/branches',
    ...options,
  });
};

export const getBranchByRepositoryIdAndName = <ThrowOnError extends boolean = false>(options: Options<GetBranchByRepositoryIdAndNameData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).get<GetBranchByRepositoryIdAndNameResponse, unknown, ThrowOnError>({
    url: '/api/branches/repository/{repoId}/branch',
    ...options,
  });
};

export const deleteWorkflowGroup = <ThrowOnError extends boolean = false>(options: Options<DeleteWorkflowGroupData, ThrowOnError>) => {
  return (options.client ?? _heyApiClient).delete<unknown, unknown, ThrowOnError>({
    url: '/api/settings/{repositoryId}/groups/{groupId}',
    ...options,
  });
};
