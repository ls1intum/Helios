// This file is auto-generated by @hey-api/openapi-ts

import type { Options } from '@hey-api/client-fetch';
import { type MutationOptions, type DefaultError, queryOptions } from '@tanstack/angular-query-experimental';
import type {
  UpdateWorkflowLabelData,
  UpdateWorkflowGroupsData,
  GetEnvironmentByIdData,
  UpdateEnvironmentData,
  UnlockEnvironmentData,
  CreateWorkflowGroupData,
  CreateWorkflowGroupResponse,
  DeployToEnvironmentData,
  DeployToEnvironmentResponse,
  HealthCheckData,
  GetAllWorkflowsData,
  GetWorkflowByIdData,
  GetWorkflowsByStateData,
  GetWorkflowsByRepositoryIdData,
  GetLatestWorkflowRunsByPullRequestIdAndHeadCommitData,
  GetLatestWorkflowRunsByBranchAndHeadCommitData,
  GetUserPermissionsData,
  GetGroupsWithWorkflowsData,
  GetAllRepositoriesData,
  GetRepositoryByIdData,
  GetAllPullRequestsData,
  GetPullRequestByIdData,
  GetPullRequestByRepositoryIdAndNumberData,
  GetPullRequestByRepositoryIdData,
  GetAllEnvironmentsData,
  GetEnvironmentsByUserLockingData,
  GetEnvironmentsByRepositoryIdData,
  GetLockHistoryByEnvironmentIdData,
  GetAllEnabledEnvironmentsData,
  GetAllDeploymentsData,
  GetDeploymentByIdData,
  GetDeploymentsByEnvironmentIdData,
  GetLatestDeploymentByEnvironmentIdData,
  GetCommitByRepositoryIdAndNameData,
  GetAllBranchesData,
  GetBranchByRepositoryIdAndNameData,
  DeleteWorkflowGroupData,
} from '../types.gen';
import {
  updateWorkflowLabel,
  updateWorkflowGroups,
  getEnvironmentById,
  updateEnvironment,
  unlockEnvironment,
  createWorkflowGroup,
  deployToEnvironment,
  healthCheck,
  getAllWorkflows,
  getWorkflowById,
  getWorkflowsByState,
  getWorkflowsByRepositoryId,
  getLatestWorkflowRunsByPullRequestIdAndHeadCommit,
  getLatestWorkflowRunsByBranchAndHeadCommit,
  getUserPermissions,
  getGroupsWithWorkflows,
  getAllRepositories,
  getRepositoryById,
  getAllPullRequests,
  getPullRequestById,
  getPullRequestByRepositoryIdAndNumber,
  getPullRequestByRepositoryId,
  getAllEnvironments,
  getEnvironmentsByUserLocking,
  getEnvironmentsByRepositoryId,
  getLockHistoryByEnvironmentId,
  getAllEnabledEnvironments,
  getAllDeployments,
  getDeploymentById,
  getDeploymentsByEnvironmentId,
  getLatestDeploymentByEnvironmentId,
  getCommitByRepositoryIdAndName,
  getAllBranches,
  getBranchByRepositoryIdAndName,
  deleteWorkflowGroup,
  client,
} from '../sdk.gen';

export const updateWorkflowLabelMutation = (options?: Partial<Options<UpdateWorkflowLabelData>>) => {
  const mutationOptions: MutationOptions<unknown, DefaultError, Options<UpdateWorkflowLabelData>> = {
    mutationFn: async localOptions => {
      const { data } = await updateWorkflowLabel({
        ...options,
        ...localOptions,
        throwOnError: true,
      });
      return data;
    },
  };
  return mutationOptions;
};

export const updateWorkflowGroupsMutation = (options?: Partial<Options<UpdateWorkflowGroupsData>>) => {
  const mutationOptions: MutationOptions<unknown, DefaultError, Options<UpdateWorkflowGroupsData>> = {
    mutationFn: async localOptions => {
      const { data } = await updateWorkflowGroups({
        ...options,
        ...localOptions,
        throwOnError: true,
      });
      return data;
    },
  };
  return mutationOptions;
};

type QueryKey<TOptions extends Options> = [
  Pick<TOptions, 'baseUrl' | 'body' | 'headers' | 'path' | 'query'> & {
    _id: string;
    _infinite?: boolean;
  },
];

const createQueryKey = <TOptions extends Options>(id: string, options?: TOptions, infinite?: boolean): QueryKey<TOptions>[0] => {
  const params: QueryKey<TOptions>[0] = { _id: id, baseUrl: (options?.client ?? client).getConfig().baseUrl } as QueryKey<TOptions>[0];
  if (infinite) {
    params._infinite = infinite;
  }
  if (options?.body) {
    params.body = options.body;
  }
  if (options?.headers) {
    params.headers = options.headers;
  }
  if (options?.path) {
    params.path = options.path;
  }
  if (options?.query) {
    params.query = options.query;
  }
  return params;
};

export const getEnvironmentByIdQueryKey = (options: Options<GetEnvironmentByIdData>) => [createQueryKey('getEnvironmentById', options)];

export const getEnvironmentByIdOptions = (options: Options<GetEnvironmentByIdData>) => {
  return queryOptions({
    queryFn: async ({ queryKey, signal }) => {
      const { data } = await getEnvironmentById({
        ...options,
        ...queryKey[0],
        signal,
        throwOnError: true,
      });
      return data;
    },
    queryKey: getEnvironmentByIdQueryKey(options),
  });
};

export const updateEnvironmentMutation = (options?: Partial<Options<UpdateEnvironmentData>>) => {
  const mutationOptions: MutationOptions<unknown, DefaultError, Options<UpdateEnvironmentData>> = {
    mutationFn: async localOptions => {
      const { data } = await updateEnvironment({
        ...options,
        ...localOptions,
        throwOnError: true,
      });
      return data;
    },
  };
  return mutationOptions;
};

export const unlockEnvironmentMutation = (options?: Partial<Options<UnlockEnvironmentData>>) => {
  const mutationOptions: MutationOptions<unknown, DefaultError, Options<UnlockEnvironmentData>> = {
    mutationFn: async localOptions => {
      const { data } = await unlockEnvironment({
        ...options,
        ...localOptions,
        throwOnError: true,
      });
      return data;
    },
  };
  return mutationOptions;
};

export const createWorkflowGroupQueryKey = (options: Options<CreateWorkflowGroupData>) => [createQueryKey('createWorkflowGroup', options)];

export const createWorkflowGroupOptions = (options: Options<CreateWorkflowGroupData>) => {
  return queryOptions({
    queryFn: async ({ queryKey, signal }) => {
      const { data } = await createWorkflowGroup({
        ...options,
        ...queryKey[0],
        signal,
        throwOnError: true,
      });
      return data;
    },
    queryKey: createWorkflowGroupQueryKey(options),
  });
};

export const createWorkflowGroupMutation = (options?: Partial<Options<CreateWorkflowGroupData>>) => {
  const mutationOptions: MutationOptions<CreateWorkflowGroupResponse, DefaultError, Options<CreateWorkflowGroupData>> = {
    mutationFn: async localOptions => {
      const { data } = await createWorkflowGroup({
        ...options,
        ...localOptions,
        throwOnError: true,
      });
      return data;
    },
  };
  return mutationOptions;
};

export const deployToEnvironmentQueryKey = (options: Options<DeployToEnvironmentData>) => [createQueryKey('deployToEnvironment', options)];

export const deployToEnvironmentOptions = (options: Options<DeployToEnvironmentData>) => {
  return queryOptions({
    queryFn: async ({ queryKey, signal }) => {
      const { data } = await deployToEnvironment({
        ...options,
        ...queryKey[0],
        signal,
        throwOnError: true,
      });
      return data;
    },
    queryKey: deployToEnvironmentQueryKey(options),
  });
};

export const deployToEnvironmentMutation = (options?: Partial<Options<DeployToEnvironmentData>>) => {
  const mutationOptions: MutationOptions<DeployToEnvironmentResponse, DefaultError, Options<DeployToEnvironmentData>> = {
    mutationFn: async localOptions => {
      const { data } = await deployToEnvironment({
        ...options,
        ...localOptions,
        throwOnError: true,
      });
      return data;
    },
  };
  return mutationOptions;
};

export const healthCheckQueryKey = (options?: Options<HealthCheckData>) => [createQueryKey('healthCheck', options)];

export const healthCheckOptions = (options?: Options<HealthCheckData>) => {
  return queryOptions({
    queryFn: async ({ queryKey, signal }) => {
      const { data } = await healthCheck({
        ...options,
        ...queryKey[0],
        signal,
        throwOnError: true,
      });
      return data;
    },
    queryKey: healthCheckQueryKey(options),
  });
};

export const getAllWorkflowsQueryKey = (options?: Options<GetAllWorkflowsData>) => [createQueryKey('getAllWorkflows', options)];

export const getAllWorkflowsOptions = (options?: Options<GetAllWorkflowsData>) => {
  return queryOptions({
    queryFn: async ({ queryKey, signal }) => {
      const { data } = await getAllWorkflows({
        ...options,
        ...queryKey[0],
        signal,
        throwOnError: true,
      });
      return data;
    },
    queryKey: getAllWorkflowsQueryKey(options),
  });
};

export const getWorkflowByIdQueryKey = (options: Options<GetWorkflowByIdData>) => [createQueryKey('getWorkflowById', options)];

export const getWorkflowByIdOptions = (options: Options<GetWorkflowByIdData>) => {
  return queryOptions({
    queryFn: async ({ queryKey, signal }) => {
      const { data } = await getWorkflowById({
        ...options,
        ...queryKey[0],
        signal,
        throwOnError: true,
      });
      return data;
    },
    queryKey: getWorkflowByIdQueryKey(options),
  });
};

export const getWorkflowsByStateQueryKey = (options: Options<GetWorkflowsByStateData>) => [createQueryKey('getWorkflowsByState', options)];

export const getWorkflowsByStateOptions = (options: Options<GetWorkflowsByStateData>) => {
  return queryOptions({
    queryFn: async ({ queryKey, signal }) => {
      const { data } = await getWorkflowsByState({
        ...options,
        ...queryKey[0],
        signal,
        throwOnError: true,
      });
      return data;
    },
    queryKey: getWorkflowsByStateQueryKey(options),
  });
};

export const getWorkflowsByRepositoryIdQueryKey = (options: Options<GetWorkflowsByRepositoryIdData>) => [createQueryKey('getWorkflowsByRepositoryId', options)];

export const getWorkflowsByRepositoryIdOptions = (options: Options<GetWorkflowsByRepositoryIdData>) => {
  return queryOptions({
    queryFn: async ({ queryKey, signal }) => {
      const { data } = await getWorkflowsByRepositoryId({
        ...options,
        ...queryKey[0],
        signal,
        throwOnError: true,
      });
      return data;
    },
    queryKey: getWorkflowsByRepositoryIdQueryKey(options),
  });
};

export const getLatestWorkflowRunsByPullRequestIdAndHeadCommitQueryKey = (options: Options<GetLatestWorkflowRunsByPullRequestIdAndHeadCommitData>) => [
  createQueryKey('getLatestWorkflowRunsByPullRequestIdAndHeadCommit', options),
];

export const getLatestWorkflowRunsByPullRequestIdAndHeadCommitOptions = (options: Options<GetLatestWorkflowRunsByPullRequestIdAndHeadCommitData>) => {
  return queryOptions({
    queryFn: async ({ queryKey, signal }) => {
      const { data } = await getLatestWorkflowRunsByPullRequestIdAndHeadCommit({
        ...options,
        ...queryKey[0],
        signal,
        throwOnError: true,
      });
      return data;
    },
    queryKey: getLatestWorkflowRunsByPullRequestIdAndHeadCommitQueryKey(options),
  });
};

export const getLatestWorkflowRunsByBranchAndHeadCommitQueryKey = (options: Options<GetLatestWorkflowRunsByBranchAndHeadCommitData>) => [
  createQueryKey('getLatestWorkflowRunsByBranchAndHeadCommit', options),
];

export const getLatestWorkflowRunsByBranchAndHeadCommitOptions = (options: Options<GetLatestWorkflowRunsByBranchAndHeadCommitData>) => {
  return queryOptions({
    queryFn: async ({ queryKey, signal }) => {
      const { data } = await getLatestWorkflowRunsByBranchAndHeadCommit({
        ...options,
        ...queryKey[0],
        signal,
        throwOnError: true,
      });
      return data;
    },
    queryKey: getLatestWorkflowRunsByBranchAndHeadCommitQueryKey(options),
  });
};

export const getUserPermissionsQueryKey = (options?: Options<GetUserPermissionsData>) => [createQueryKey('getUserPermissions', options)];

export const getUserPermissionsOptions = (options?: Options<GetUserPermissionsData>) => {
  return queryOptions({
    queryFn: async ({ queryKey, signal }) => {
      const { data } = await getUserPermissions({
        ...options,
        ...queryKey[0],
        signal,
        throwOnError: true,
      });
      return data;
    },
    queryKey: getUserPermissionsQueryKey(options),
  });
};

export const getGroupsWithWorkflowsQueryKey = (options: Options<GetGroupsWithWorkflowsData>) => [createQueryKey('getGroupsWithWorkflows', options)];

export const getGroupsWithWorkflowsOptions = (options: Options<GetGroupsWithWorkflowsData>) => {
  return queryOptions({
    queryFn: async ({ queryKey, signal }) => {
      const { data } = await getGroupsWithWorkflows({
        ...options,
        ...queryKey[0],
        signal,
        throwOnError: true,
      });
      return data;
    },
    queryKey: getGroupsWithWorkflowsQueryKey(options),
  });
};

export const getAllRepositoriesQueryKey = (options?: Options<GetAllRepositoriesData>) => [createQueryKey('getAllRepositories', options)];

export const getAllRepositoriesOptions = (options?: Options<GetAllRepositoriesData>) => {
  return queryOptions({
    queryFn: async ({ queryKey, signal }) => {
      const { data } = await getAllRepositories({
        ...options,
        ...queryKey[0],
        signal,
        throwOnError: true,
      });
      return data;
    },
    queryKey: getAllRepositoriesQueryKey(options),
  });
};

export const getRepositoryByIdQueryKey = (options: Options<GetRepositoryByIdData>) => [createQueryKey('getRepositoryById', options)];

export const getRepositoryByIdOptions = (options: Options<GetRepositoryByIdData>) => {
  return queryOptions({
    queryFn: async ({ queryKey, signal }) => {
      const { data } = await getRepositoryById({
        ...options,
        ...queryKey[0],
        signal,
        throwOnError: true,
      });
      return data;
    },
    queryKey: getRepositoryByIdQueryKey(options),
  });
};

export const getAllPullRequestsQueryKey = (options?: Options<GetAllPullRequestsData>) => [createQueryKey('getAllPullRequests', options)];

export const getAllPullRequestsOptions = (options?: Options<GetAllPullRequestsData>) => {
  return queryOptions({
    queryFn: async ({ queryKey, signal }) => {
      const { data } = await getAllPullRequests({
        ...options,
        ...queryKey[0],
        signal,
        throwOnError: true,
      });
      return data;
    },
    queryKey: getAllPullRequestsQueryKey(options),
  });
};

export const getPullRequestByIdQueryKey = (options: Options<GetPullRequestByIdData>) => [createQueryKey('getPullRequestById', options)];

export const getPullRequestByIdOptions = (options: Options<GetPullRequestByIdData>) => {
  return queryOptions({
    queryFn: async ({ queryKey, signal }) => {
      const { data } = await getPullRequestById({
        ...options,
        ...queryKey[0],
        signal,
        throwOnError: true,
      });
      return data;
    },
    queryKey: getPullRequestByIdQueryKey(options),
  });
};

export const getPullRequestByRepositoryIdAndNumberQueryKey = (options: Options<GetPullRequestByRepositoryIdAndNumberData>) => [
  createQueryKey('getPullRequestByRepositoryIdAndNumber', options),
];

export const getPullRequestByRepositoryIdAndNumberOptions = (options: Options<GetPullRequestByRepositoryIdAndNumberData>) => {
  return queryOptions({
    queryFn: async ({ queryKey, signal }) => {
      const { data } = await getPullRequestByRepositoryIdAndNumber({
        ...options,
        ...queryKey[0],
        signal,
        throwOnError: true,
      });
      return data;
    },
    queryKey: getPullRequestByRepositoryIdAndNumberQueryKey(options),
  });
};

export const getPullRequestByRepositoryIdQueryKey = (options: Options<GetPullRequestByRepositoryIdData>) => [createQueryKey('getPullRequestByRepositoryId', options)];

export const getPullRequestByRepositoryIdOptions = (options: Options<GetPullRequestByRepositoryIdData>) => {
  return queryOptions({
    queryFn: async ({ queryKey, signal }) => {
      const { data } = await getPullRequestByRepositoryId({
        ...options,
        ...queryKey[0],
        signal,
        throwOnError: true,
      });
      return data;
    },
    queryKey: getPullRequestByRepositoryIdQueryKey(options),
  });
};

export const getAllEnvironmentsQueryKey = (options?: Options<GetAllEnvironmentsData>) => [createQueryKey('getAllEnvironments', options)];

export const getAllEnvironmentsOptions = (options?: Options<GetAllEnvironmentsData>) => {
  return queryOptions({
    queryFn: async ({ queryKey, signal }) => {
      const { data } = await getAllEnvironments({
        ...options,
        ...queryKey[0],
        signal,
        throwOnError: true,
      });
      return data;
    },
    queryKey: getAllEnvironmentsQueryKey(options),
  });
};

export const getEnvironmentsByUserLockingQueryKey = (options?: Options<GetEnvironmentsByUserLockingData>) => [createQueryKey('getEnvironmentsByUserLocking', options)];

export const getEnvironmentsByUserLockingOptions = (options?: Options<GetEnvironmentsByUserLockingData>) => {
  return queryOptions({
    queryFn: async ({ queryKey, signal }) => {
      const { data } = await getEnvironmentsByUserLocking({
        ...options,
        ...queryKey[0],
        signal,
        throwOnError: true,
      });
      return data;
    },
    queryKey: getEnvironmentsByUserLockingQueryKey(options),
  });
};

export const getEnvironmentsByRepositoryIdQueryKey = (options: Options<GetEnvironmentsByRepositoryIdData>) => [createQueryKey('getEnvironmentsByRepositoryId', options)];

export const getEnvironmentsByRepositoryIdOptions = (options: Options<GetEnvironmentsByRepositoryIdData>) => {
  return queryOptions({
    queryFn: async ({ queryKey, signal }) => {
      const { data } = await getEnvironmentsByRepositoryId({
        ...options,
        ...queryKey[0],
        signal,
        throwOnError: true,
      });
      return data;
    },
    queryKey: getEnvironmentsByRepositoryIdQueryKey(options),
  });
};

export const getLockHistoryByEnvironmentIdQueryKey = (options: Options<GetLockHistoryByEnvironmentIdData>) => [createQueryKey('getLockHistoryByEnvironmentId', options)];

export const getLockHistoryByEnvironmentIdOptions = (options: Options<GetLockHistoryByEnvironmentIdData>) => {
  return queryOptions({
    queryFn: async ({ queryKey, signal }) => {
      const { data } = await getLockHistoryByEnvironmentId({
        ...options,
        ...queryKey[0],
        signal,
        throwOnError: true,
      });
      return data;
    },
    queryKey: getLockHistoryByEnvironmentIdQueryKey(options),
  });
};

export const getAllEnabledEnvironmentsQueryKey = (options?: Options<GetAllEnabledEnvironmentsData>) => [createQueryKey('getAllEnabledEnvironments', options)];

export const getAllEnabledEnvironmentsOptions = (options?: Options<GetAllEnabledEnvironmentsData>) => {
  return queryOptions({
    queryFn: async ({ queryKey, signal }) => {
      const { data } = await getAllEnabledEnvironments({
        ...options,
        ...queryKey[0],
        signal,
        throwOnError: true,
      });
      return data;
    },
    queryKey: getAllEnabledEnvironmentsQueryKey(options),
  });
};

export const getAllDeploymentsQueryKey = (options?: Options<GetAllDeploymentsData>) => [createQueryKey('getAllDeployments', options)];

export const getAllDeploymentsOptions = (options?: Options<GetAllDeploymentsData>) => {
  return queryOptions({
    queryFn: async ({ queryKey, signal }) => {
      const { data } = await getAllDeployments({
        ...options,
        ...queryKey[0],
        signal,
        throwOnError: true,
      });
      return data;
    },
    queryKey: getAllDeploymentsQueryKey(options),
  });
};

export const getDeploymentByIdQueryKey = (options: Options<GetDeploymentByIdData>) => [createQueryKey('getDeploymentById', options)];

export const getDeploymentByIdOptions = (options: Options<GetDeploymentByIdData>) => {
  return queryOptions({
    queryFn: async ({ queryKey, signal }) => {
      const { data } = await getDeploymentById({
        ...options,
        ...queryKey[0],
        signal,
        throwOnError: true,
      });
      return data;
    },
    queryKey: getDeploymentByIdQueryKey(options),
  });
};

export const getDeploymentsByEnvironmentIdQueryKey = (options: Options<GetDeploymentsByEnvironmentIdData>) => [createQueryKey('getDeploymentsByEnvironmentId', options)];

export const getDeploymentsByEnvironmentIdOptions = (options: Options<GetDeploymentsByEnvironmentIdData>) => {
  return queryOptions({
    queryFn: async ({ queryKey, signal }) => {
      const { data } = await getDeploymentsByEnvironmentId({
        ...options,
        ...queryKey[0],
        signal,
        throwOnError: true,
      });
      return data;
    },
    queryKey: getDeploymentsByEnvironmentIdQueryKey(options),
  });
};

export const getLatestDeploymentByEnvironmentIdQueryKey = (options: Options<GetLatestDeploymentByEnvironmentIdData>) => [
  createQueryKey('getLatestDeploymentByEnvironmentId', options),
];

export const getLatestDeploymentByEnvironmentIdOptions = (options: Options<GetLatestDeploymentByEnvironmentIdData>) => {
  return queryOptions({
    queryFn: async ({ queryKey, signal }) => {
      const { data } = await getLatestDeploymentByEnvironmentId({
        ...options,
        ...queryKey[0],
        signal,
        throwOnError: true,
      });
      return data;
    },
    queryKey: getLatestDeploymentByEnvironmentIdQueryKey(options),
  });
};

export const getCommitByRepositoryIdAndNameQueryKey = (options: Options<GetCommitByRepositoryIdAndNameData>) => [createQueryKey('getCommitByRepositoryIdAndName', options)];

export const getCommitByRepositoryIdAndNameOptions = (options: Options<GetCommitByRepositoryIdAndNameData>) => {
  return queryOptions({
    queryFn: async ({ queryKey, signal }) => {
      const { data } = await getCommitByRepositoryIdAndName({
        ...options,
        ...queryKey[0],
        signal,
        throwOnError: true,
      });
      return data;
    },
    queryKey: getCommitByRepositoryIdAndNameQueryKey(options),
  });
};

export const getAllBranchesQueryKey = (options?: Options<GetAllBranchesData>) => [createQueryKey('getAllBranches', options)];

export const getAllBranchesOptions = (options?: Options<GetAllBranchesData>) => {
  return queryOptions({
    queryFn: async ({ queryKey, signal }) => {
      const { data } = await getAllBranches({
        ...options,
        ...queryKey[0],
        signal,
        throwOnError: true,
      });
      return data;
    },
    queryKey: getAllBranchesQueryKey(options),
  });
};

export const getBranchByRepositoryIdAndNameQueryKey = (options: Options<GetBranchByRepositoryIdAndNameData>) => [createQueryKey('getBranchByRepositoryIdAndName', options)];

export const getBranchByRepositoryIdAndNameOptions = (options: Options<GetBranchByRepositoryIdAndNameData>) => {
  return queryOptions({
    queryFn: async ({ queryKey, signal }) => {
      const { data } = await getBranchByRepositoryIdAndName({
        ...options,
        ...queryKey[0],
        signal,
        throwOnError: true,
      });
      return data;
    },
    queryKey: getBranchByRepositoryIdAndNameQueryKey(options),
  });
};

export const deleteWorkflowGroupMutation = (options?: Partial<Options<DeleteWorkflowGroupData>>) => {
  const mutationOptions: MutationOptions<unknown, DefaultError, Options<DeleteWorkflowGroupData>> = {
    mutationFn: async localOptions => {
      const { data } = await deleteWorkflowGroup({
        ...options,
        ...localOptions,
        throwOnError: true,
      });
      return data;
    },
  };
  return mutationOptions;
};
