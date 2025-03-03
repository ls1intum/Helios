// This file is auto-generated by @hey-api/openapi-ts

export type GitRepoSettingsDto = {
  id?: number;
  lockExpirationThreshold?: number;
  lockReservationThreshold?: number;
};

export type WorkflowGroupDto = {
  id: number;
  name: string;
  orderIndex: number;
  memberships?: Array<WorkflowMembershipDto>;
};

export type WorkflowMembershipDto = {
  workflowId: number;
  orderIndex: number;
};

export type EnvironmentDeployment = {
  id: number;
  url?: string;
  state?: 'REQUESTED' | 'PENDING' | 'WAITING' | 'SUCCESS' | 'ERROR' | 'FAILURE' | 'IN_PROGRESS' | 'QUEUED' | 'INACTIVE' | 'UNKNOWN';
  statusesUrl?: string;
  sha?: string;
  ref?: string;
  task?: string;
  workflowRunHtmlUrl?: string;
  releaseCandidateName?: string;
  prName?: string;
  user?: UserInfoDto;
  createdAt?: string;
  updatedAt?: string;
  type: 'GITHUB' | 'HELIOS';
};

export type EnvironmentDto = {
  repository?: RepositoryInfoDto;
  id: number;
  name: string;
  locked?: boolean;
  url?: string;
  htmlUrl?: string;
  createdAt?: string;
  updatedAt?: string;
  enabled?: boolean;
  installedApps?: Array<string>;
  description?: string;
  serverUrl?: string;
  statusCheckType?: 'HTTP_STATUS' | 'ARTEMIS_INFO';
  statusUrl?: string;
  latestDeployment?: EnvironmentDeployment;
  latestStatus?: EnvironmentStatusDto;
  lockedBy?: UserInfoDto;
  lockedAt?: string;
  type?: 'TEST' | 'STAGING' | 'PRODUCTION';
  lockExpirationThreshold?: number;
  lockReservationThreshold?: number;
  lockWillExpireAt?: string;
  lockReservationWillExpireAt?: string;
};

export type EnvironmentStatusDto = {
  id: number;
  success: boolean;
  httpStatusCode: number;
  checkedAt: string;
  checkType: 'HTTP_STATUS' | 'ARTEMIS_INFO';
  metadata?: {
    [key: string]: {
      [key: string]: unknown;
    };
  };
};

export type RepositoryInfoDto = {
  id: number;
  name: string;
  nameWithOwner: string;
  description?: string;
  htmlUrl: string;
};

export type UserInfoDto = {
  id: number;
  login: string;
  avatarUrl: string;
  name: string;
  htmlUrl: string;
};

export type ReleaseCandidateCreateDto = {
  name: string;
  commitSha: string;
  branchName: string;
};

export type ReleaseCandidateInfoDto = {
  name?: string;
  commitSha?: string;
  branchName?: string;
};

export type DeployRequest = {
  environmentId: number;
  branchName: string;
  commitSha?: string;
};

export type WorkflowDto = {
  id: number;
  repository?: RepositoryInfoDto;
  name: string;
  path: string;
  fileNameWithExtension?: string;
  state: 'ACTIVE' | 'DELETED' | 'DISABLED_FORK' | 'DISABLED_INACTIVITY' | 'DISABLED_MANUALLY' | 'UNKNOWN';
  url?: string;
  htmlUrl?: string;
  badgeUrl?: string;
  label: 'NONE' | 'DEPLOY_TEST_SERVER' | 'DEPLOY_STAGING_SERVER' | 'DEPLOY_PRODUCTION_SERVER' | 'TEST';
  createdAt?: string;
  updatedAt?: string;
};

export type WorkflowRunDto = {
  id: number;
  name: string;
  displayTitle: string;
  status:
    | 'QUEUED'
    | 'IN_PROGRESS'
    | 'COMPLETED'
    | 'ACTION_REQUIRED'
    | 'CANCELLED'
    | 'FAILURE'
    | 'NEUTRAL'
    | 'SKIPPED'
    | 'STALE'
    | 'SUCCESS'
    | 'TIMED_OUT'
    | 'REQUESTED'
    | 'WAITING'
    | 'PENDING'
    | 'UNKNOWN';
  workflowId: number;
  conclusion?: 'ACTION_REQUIRED' | 'CANCELLED' | 'FAILURE' | 'NEUTRAL' | 'SUCCESS' | 'SKIPPED' | 'STALE' | 'TIMED_OUT' | 'STARTUP_FAILURE' | 'UNKNOWN';
  htmlUrl: string;
  label: 'NONE' | 'DEPLOY_TEST_SERVER' | 'DEPLOY_STAGING_SERVER' | 'DEPLOY_PRODUCTION_SERVER' | 'TEST';
  testProcessingStatus?: 'PROCESSING' | 'PROCESSED' | 'FAILED';
};

export type GitHubRepositoryRoleDto = {
  permission?: 'ADMIN' | 'WRITE' | 'READ' | 'NONE';
  roleName?: string;
};

export type TestCaseDto = {
  id: number;
  name: string;
  className: string;
  status: 'PASSED' | 'FAILED' | 'ERROR' | 'SKIPPED';
  previousStatusProvider?: 'PASSED' | 'FAILED' | 'ERROR' | 'SKIPPED';
  time: number;
  message?: string;
  stackTrace?: string;
  errorType?: string;
};

export type TestResultsDto = {
  testSuites: Array<TestSuiteDto>;
  isProcessing?: boolean;
};

export type TestSuiteDto = {
  id: number;
  name: string;
  timestamp: string;
  tests: number;
  failures: number;
  errors: number;
  skipped: number;
  time: number;
  testCases: Array<TestCaseDto>;
};

export type BranchInfoDto = {
  name: string;
  commitSha: string;
  aheadBy?: number;
  behindBy?: number;
  isDefault?: boolean;
  isProtected?: boolean;
  isPinned?: boolean;
  updatedAt?: string;
  updatedBy?: UserInfoDto;
  repository?: RepositoryInfoDto;
};

export type CommitInfoDto = {
  sha: string;
  author?: UserInfoDto;
  message?: string;
  authoredAt?: string;
  repository?: RepositoryInfoDto;
};

export type ReleaseCandidateDeploymentDto = {
  id: number;
  type: 'GITHUB' | 'HELIOS';
  environmentId: number;
};

export type ReleaseCandidateDetailsDto = {
  name: string;
  commit: CommitInfoDto;
  branch: BranchInfoDto;
  deployments: Array<ReleaseCandidateDeploymentDto>;
  evaluations: Array<ReleaseCandidateEvaluationDto>;
  createdBy: UserInfoDto;
  createdAt: string;
};

export type ReleaseCandidateEvaluationDto = {
  user: UserInfoDto;
  isWorking: boolean;
};

export type CommitsSinceReleaseCandidateDto = {
  commitsLength: number;
  commits: Array<CommitInfoDto>;
};

export type LabelInfoDto = {
  /**
   * The unique identifier of the label
   */
  id: number;
  /**
   * The name of the label
   */
  name: string;
  /**
   * The color of the label as a 6-character hex code (without #)
   */
  color: string;
  /**
   * The repository associated with this label
   */
  repository?: RepositoryInfoDto;
};

export type PullRequestBaseInfoDto = {
  id: number;
  number: number;
  title: string;
  state: 'OPEN' | 'CLOSED';
  isDraft: boolean;
  isMerged: boolean;
  isPinned?: boolean;
  htmlUrl: string;
  createdAt?: string;
  updatedAt?: string;
  author?: UserInfoDto;
  labels?: Array<LabelInfoDto>;
  assignees?: Array<UserInfoDto>;
  reviewers?: Array<UserInfoDto>;
};

export type PullRequestInfoDto = {
  id: number;
  number: number;
  title: string;
  state: 'OPEN' | 'CLOSED';
  isDraft: boolean;
  isMerged: boolean;
  commentsCount: number;
  author?: UserInfoDto;
  labels?: Array<LabelInfoDto>;
  assignees?: Array<UserInfoDto>;
  repository?: RepositoryInfoDto;
  additions: number;
  deletions: number;
  headSha: string;
  headRefName: string;
  headRefRepoNameWithOwner: string;
  mergedAt?: string;
  closedAt?: string;
  htmlUrl: string;
  createdAt?: string;
  updatedAt?: string;
};

export type EnvironmentLockHistoryDto = {
  id: number;
  lockedBy?: UserInfoDto;
  unlockedBy?: UserInfoDto;
  lockedAt?: string;
  unlockedAt?: string;
  environment?: EnvironmentDto;
};

export type DeploymentDto = {
  id: number;
  repository?: RepositoryInfoDto;
  url: string;
  state?: 'PENDING' | 'WAITING' | 'SUCCESS' | 'ERROR' | 'FAILURE' | 'IN_PROGRESS' | 'QUEUED' | 'INACTIVE' | 'UNKNOWN';
  statusesUrl: string;
  sha: string;
  ref: string;
  task: string;
  environment: EnvironmentDto;
  user?: UserInfoDto;
  createdAt?: string;
  updatedAt?: string;
};

export type ActivityHistoryDto = {
  type?: string;
  id?: number;
  repository?: RepositoryInfoDto;
  state?: 'PENDING' | 'WAITING' | 'SUCCESS' | 'ERROR' | 'FAILURE' | 'IN_PROGRESS' | 'QUEUED' | 'INACTIVE' | 'UNKNOWN';
  sha?: string;
  ref?: string;
  user?: UserInfoDto;
  user2?: UserInfoDto;
  timestamp?: string;
  createdAt?: string;
  updatedAt?: string;
};

export type BranchDetailsDto = {
  name: string;
  commitSha: string;
  aheadBy?: number;
  behindBy?: number;
  isDefault?: boolean;
  isProtected?: boolean;
  releaseCandidateName?: string;
  updatedAt?: string;
  updatedBy?: UserInfoDto;
  repository?: RepositoryInfoDto;
};

export type UpdateWorkflowLabelData = {
  body: 'NONE' | 'DEPLOY_TEST_SERVER' | 'DEPLOY_STAGING_SERVER' | 'DEPLOY_PRODUCTION_SERVER' | 'TEST';
  path: {
    workflowId: number;
  };
  query?: never;
  url: '/api/workflows/{workflowId}/label';
};

export type UpdateWorkflowLabelResponses = {
  /**
   * OK
   */
  200: unknown;
};

export type GetGitRepoSettingsData = {
  body?: never;
  path: {
    repositoryId: number;
  };
  query?: never;
  url: '/api/settings/{repositoryId}/settings';
};

export type GetGitRepoSettingsResponses = {
  /**
   * OK
   */
  200: GitRepoSettingsDto;
};

export type GetGitRepoSettingsResponse = GetGitRepoSettingsResponses[keyof GetGitRepoSettingsResponses];

export type UpdateGitRepoSettingsData = {
  body: GitRepoSettingsDto;
  path: {
    repositoryId: number;
  };
  query?: never;
  url: '/api/settings/{repositoryId}/settings';
};

export type UpdateGitRepoSettingsResponses = {
  /**
   * OK
   */
  200: {
    [key: string]: unknown;
  };
};

export type UpdateGitRepoSettingsResponse = UpdateGitRepoSettingsResponses[keyof UpdateGitRepoSettingsResponses];

export type UpdateWorkflowGroupsData = {
  body: Array<WorkflowGroupDto>;
  path: {
    repositoryId: number;
  };
  query?: never;
  url: '/api/settings/{repositoryId}/groups/update';
};

export type UpdateWorkflowGroupsResponses = {
  /**
   * OK
   */
  200: unknown;
};

export type GetEnvironmentByIdData = {
  body?: never;
  path: {
    id: number;
  };
  query?: never;
  url: '/api/environments/{id}';
};

export type GetEnvironmentByIdResponses = {
  /**
   * OK
   */
  200: EnvironmentDto;
};

export type GetEnvironmentByIdResponse = GetEnvironmentByIdResponses[keyof GetEnvironmentByIdResponses];

export type UpdateEnvironmentData = {
  body: EnvironmentDto;
  path: {
    id: number;
  };
  query?: never;
  url: '/api/environments/{id}';
};

export type UpdateEnvironmentResponses = {
  /**
   * OK
   */
  200: {
    [key: string]: unknown;
  };
};

export type UpdateEnvironmentResponse = UpdateEnvironmentResponses[keyof UpdateEnvironmentResponses];

export type UnlockEnvironmentData = {
  body?: never;
  path: {
    id: number;
  };
  query?: never;
  url: '/api/environments/{id}/unlock';
};

export type UnlockEnvironmentResponses = {
  /**
   * OK
   */
  200: {
    [key: string]: unknown;
  };
};

export type UnlockEnvironmentResponse = UnlockEnvironmentResponses[keyof UnlockEnvironmentResponses];

export type LockEnvironmentData = {
  body?: never;
  path: {
    id: number;
  };
  query?: never;
  url: '/api/environments/{id}/lock';
};

export type LockEnvironmentResponses = {
  /**
   * OK
   */
  200: {
    [key: string]: unknown;
  };
};

export type LockEnvironmentResponse = LockEnvironmentResponses[keyof LockEnvironmentResponses];

export type CreateWorkflowGroupData = {
  body: WorkflowGroupDto;
  path: {
    repositoryId: number;
  };
  query?: never;
  url: '/api/settings/{repositoryId}/groups/create';
};

export type CreateWorkflowGroupResponses = {
  /**
   * OK
   */
  200: WorkflowGroupDto;
};

export type CreateWorkflowGroupResponse = CreateWorkflowGroupResponses[keyof CreateWorkflowGroupResponses];

export type GetAllReleaseCandidatesData = {
  body?: never;
  path?: never;
  query?: never;
  url: '/api/release-candidate';
};

export type GetAllReleaseCandidatesResponses = {
  /**
   * OK
   */
  200: Array<ReleaseCandidateInfoDto>;
};

export type GetAllReleaseCandidatesResponse = GetAllReleaseCandidatesResponses[keyof GetAllReleaseCandidatesResponses];

export type CreateReleaseCandidateData = {
  body: ReleaseCandidateCreateDto;
  path?: never;
  query?: never;
  url: '/api/release-candidate';
};

export type CreateReleaseCandidateResponses = {
  /**
   * OK
   */
  200: ReleaseCandidateInfoDto;
};

export type CreateReleaseCandidateResponse = CreateReleaseCandidateResponses[keyof CreateReleaseCandidateResponses];

export type EvaluateData = {
  body?: never;
  path: {
    name: string;
    isWorking: boolean;
  };
  query?: never;
  url: '/api/release-candidate/{name}/evaluate/{isWorking}';
};

export type EvaluateResponses = {
  /**
   * OK
   */
  200: unknown;
};

export type SetPrPinnedByNumberData = {
  body?: never;
  path: {
    pr: number;
  };
  query: {
    isPinned: boolean;
  };
  url: '/api/pullrequests/{pr}/pin';
};

export type SetPrPinnedByNumberResponses = {
  /**
   * OK
   */
  200: unknown;
};

export type DeployToEnvironmentData = {
  body: DeployRequest;
  path?: never;
  query?: never;
  url: '/api/deployments/deploy';
};

export type DeployToEnvironmentResponses = {
  /**
   * OK
   */
  200: string;
};

export type DeployToEnvironmentResponse = DeployToEnvironmentResponses[keyof DeployToEnvironmentResponses];

export type SetBranchPinnedByRepositoryIdAndNameAndUserIdData = {
  body?: never;
  path: {
    repoId: number;
  };
  query: {
    name: string;
    isPinned: boolean;
  };
  url: '/api/branches/repository/{repoId}/pin';
};

export type SetBranchPinnedByRepositoryIdAndNameAndUserIdResponses = {
  /**
   * OK
   */
  200: unknown;
};

export type HealthCheckData = {
  body?: never;
  path?: never;
  query?: never;
  url: '/status/health';
};

export type HealthCheckResponses = {
  /**
   * OK
   */
  200: string;
};

export type HealthCheckResponse = HealthCheckResponses[keyof HealthCheckResponses];

export type GetAllWorkflowsData = {
  body?: never;
  path?: never;
  query?: never;
  url: '/api/workflows';
};

export type GetAllWorkflowsResponses = {
  /**
   * OK
   */
  200: Array<WorkflowDto>;
};

export type GetAllWorkflowsResponse = GetAllWorkflowsResponses[keyof GetAllWorkflowsResponses];

export type GetWorkflowByIdData = {
  body?: never;
  path: {
    id: number;
  };
  query?: never;
  url: '/api/workflows/{id}';
};

export type GetWorkflowByIdResponses = {
  /**
   * OK
   */
  200: WorkflowDto;
};

export type GetWorkflowByIdResponse = GetWorkflowByIdResponses[keyof GetWorkflowByIdResponses];

export type GetWorkflowsByStateData = {
  body?: never;
  path: {
    state: 'ACTIVE' | 'DELETED' | 'DISABLED_FORK' | 'DISABLED_INACTIVITY' | 'DISABLED_MANUALLY' | 'UNKNOWN';
  };
  query?: never;
  url: '/api/workflows/state/{state}';
};

export type GetWorkflowsByStateResponses = {
  /**
   * OK
   */
  200: Array<WorkflowDto>;
};

export type GetWorkflowsByStateResponse = GetWorkflowsByStateResponses[keyof GetWorkflowsByStateResponses];

export type GetWorkflowsByRepositoryIdData = {
  body?: never;
  path: {
    repositoryId: number;
  };
  query?: never;
  url: '/api/workflows/repository/{repositoryId}';
};

export type GetWorkflowsByRepositoryIdResponses = {
  /**
   * OK
   */
  200: Array<WorkflowDto>;
};

export type GetWorkflowsByRepositoryIdResponse = GetWorkflowsByRepositoryIdResponses[keyof GetWorkflowsByRepositoryIdResponses];

export type GetLatestWorkflowRunsByPullRequestIdAndHeadCommitData = {
  body?: never;
  path: {
    pullRequestId: number;
  };
  query?: never;
  url: '/api/workflows/pr/{pullRequestId}';
};

export type GetLatestWorkflowRunsByPullRequestIdAndHeadCommitResponses = {
  /**
   * OK
   */
  200: Array<WorkflowRunDto>;
};

export type GetLatestWorkflowRunsByPullRequestIdAndHeadCommitResponse =
  GetLatestWorkflowRunsByPullRequestIdAndHeadCommitResponses[keyof GetLatestWorkflowRunsByPullRequestIdAndHeadCommitResponses];

export type GetLatestWorkflowRunsByBranchAndHeadCommitData = {
  body?: never;
  path?: never;
  query: {
    branch: string;
  };
  url: '/api/workflows/branch';
};

export type GetLatestWorkflowRunsByBranchAndHeadCommitResponses = {
  /**
   * OK
   */
  200: Array<WorkflowRunDto>;
};

export type GetLatestWorkflowRunsByBranchAndHeadCommitResponse = GetLatestWorkflowRunsByBranchAndHeadCommitResponses[keyof GetLatestWorkflowRunsByBranchAndHeadCommitResponses];

export type GetUserPermissionsData = {
  body?: never;
  path?: never;
  query?: never;
  url: '/api/user-permissions';
};

export type GetUserPermissionsResponses = {
  /**
   * OK
   */
  200: GitHubRepositoryRoleDto;
};

export type GetUserPermissionsResponse = GetUserPermissionsResponses[keyof GetUserPermissionsResponses];

export type GetLatestTestResultsByPullRequestIdData = {
  body?: never;
  path: {
    pullRequestId: number;
  };
  query?: never;
  url: '/api/tests/pr/{pullRequestId}';
};

export type GetLatestTestResultsByPullRequestIdResponses = {
  /**
   * OK
   */
  200: TestResultsDto;
};

export type GetLatestTestResultsByPullRequestIdResponse = GetLatestTestResultsByPullRequestIdResponses[keyof GetLatestTestResultsByPullRequestIdResponses];

export type GetLatestTestResultsByBranchData = {
  body?: never;
  path?: never;
  query: {
    branch: string;
  };
  url: '/api/tests/branch';
};

export type GetLatestTestResultsByBranchResponses = {
  /**
   * OK
   */
  200: TestResultsDto;
};

export type GetLatestTestResultsByBranchResponse = GetLatestTestResultsByBranchResponses[keyof GetLatestTestResultsByBranchResponses];

export type GetGroupsWithWorkflowsData = {
  body?: never;
  path: {
    repositoryId: number;
  };
  query?: never;
  url: '/api/settings/{repositoryId}/groups';
};

export type GetGroupsWithWorkflowsResponses = {
  /**
   * OK
   */
  200: Array<WorkflowGroupDto>;
};

export type GetGroupsWithWorkflowsResponse = GetGroupsWithWorkflowsResponses[keyof GetGroupsWithWorkflowsResponses];

export type GetAllRepositoriesData = {
  body?: never;
  path?: never;
  query?: never;
  url: '/api/repository';
};

export type GetAllRepositoriesResponses = {
  /**
   * OK
   */
  200: Array<RepositoryInfoDto>;
};

export type GetAllRepositoriesResponse = GetAllRepositoriesResponses[keyof GetAllRepositoriesResponses];

export type GetRepositoryByIdData = {
  body?: never;
  path: {
    id: number;
  };
  query?: never;
  url: '/api/repository/{id}';
};

export type GetRepositoryByIdResponses = {
  /**
   * OK
   */
  200: RepositoryInfoDto;
};

export type GetRepositoryByIdResponse = GetRepositoryByIdResponses[keyof GetRepositoryByIdResponses];

export type DeleteReleaseCandidateByNameData = {
  body?: never;
  path: {
    name: string;
  };
  query?: never;
  url: '/api/release-candidate/{name}';
};

export type DeleteReleaseCandidateByNameResponses = {
  /**
   * OK
   */
  200: ReleaseCandidateInfoDto;
};

export type DeleteReleaseCandidateByNameResponse = DeleteReleaseCandidateByNameResponses[keyof DeleteReleaseCandidateByNameResponses];

export type GetReleaseCandidateByNameData = {
  body?: never;
  path: {
    name: string;
  };
  query?: never;
  url: '/api/release-candidate/{name}';
};

export type GetReleaseCandidateByNameResponses = {
  /**
   * OK
   */
  200: ReleaseCandidateDetailsDto;
};

export type GetReleaseCandidateByNameResponse = GetReleaseCandidateByNameResponses[keyof GetReleaseCandidateByNameResponses];

export type GetCommitsSinceLastReleaseCandidateData = {
  body?: never;
  path?: never;
  query: {
    branch: string;
  };
  url: '/api/release-candidate/newcommits';
};

export type GetCommitsSinceLastReleaseCandidateResponses = {
  /**
   * OK
   */
  200: CommitsSinceReleaseCandidateDto;
};

export type GetCommitsSinceLastReleaseCandidateResponse = GetCommitsSinceLastReleaseCandidateResponses[keyof GetCommitsSinceLastReleaseCandidateResponses];

export type GetAllPullRequestsData = {
  body?: never;
  path?: never;
  query?: never;
  url: '/api/pullrequests';
};

export type GetAllPullRequestsResponses = {
  /**
   * OK
   */
  200: Array<PullRequestBaseInfoDto>;
};

export type GetAllPullRequestsResponse = GetAllPullRequestsResponses[keyof GetAllPullRequestsResponses];

export type GetPullRequestByIdData = {
  body?: never;
  path: {
    id: number;
  };
  query?: never;
  url: '/api/pullrequests/{id}';
};

export type GetPullRequestByIdResponses = {
  /**
   * OK
   */
  200: PullRequestInfoDto;
};

export type GetPullRequestByIdResponse = GetPullRequestByIdResponses[keyof GetPullRequestByIdResponses];

export type GetPullRequestByRepositoryIdAndNumberData = {
  body?: never;
  path: {
    repoId: number;
    number: number;
  };
  query?: never;
  url: '/api/pullrequests/repository/{repoId}/pr/{number}';
};

export type GetPullRequestByRepositoryIdAndNumberResponses = {
  /**
   * OK
   */
  200: PullRequestInfoDto;
};

export type GetPullRequestByRepositoryIdAndNumberResponse = GetPullRequestByRepositoryIdAndNumberResponses[keyof GetPullRequestByRepositoryIdAndNumberResponses];

export type GetPullRequestByRepositoryIdData = {
  body?: never;
  path: {
    id: number;
  };
  query?: never;
  url: '/api/pullrequests/repository/{id}';
};

export type GetPullRequestByRepositoryIdResponses = {
  /**
   * OK
   */
  200: Array<PullRequestInfoDto>;
};

export type GetPullRequestByRepositoryIdResponse = GetPullRequestByRepositoryIdResponses[keyof GetPullRequestByRepositoryIdResponses];

export type GetAllEnvironmentsData = {
  body?: never;
  path?: never;
  query?: never;
  url: '/api/environments';
};

export type GetAllEnvironmentsResponses = {
  /**
   * OK
   */
  200: Array<EnvironmentDto>;
};

export type GetAllEnvironmentsResponse = GetAllEnvironmentsResponses[keyof GetAllEnvironmentsResponses];

export type GetEnvironmentsByUserLockingData = {
  body?: never;
  path?: never;
  query?: never;
  url: '/api/environments/userLocking';
};

export type GetEnvironmentsByUserLockingResponses = {
  /**
   * OK
   */
  200: EnvironmentLockHistoryDto;
};

export type GetEnvironmentsByUserLockingResponse = GetEnvironmentsByUserLockingResponses[keyof GetEnvironmentsByUserLockingResponses];

export type GetEnvironmentsByRepositoryIdData = {
  body?: never;
  path: {
    repositoryId: number;
  };
  query?: never;
  url: '/api/environments/repository/{repositoryId}';
};

export type GetEnvironmentsByRepositoryIdResponses = {
  /**
   * OK
   */
  200: Array<EnvironmentDto>;
};

export type GetEnvironmentsByRepositoryIdResponse = GetEnvironmentsByRepositoryIdResponses[keyof GetEnvironmentsByRepositoryIdResponses];

export type GetLockHistoryByEnvironmentIdData = {
  body?: never;
  path: {
    environmentId: number;
  };
  query?: never;
  url: '/api/environments/environment/{environmentId}/lockHistory';
};

export type GetLockHistoryByEnvironmentIdResponses = {
  /**
   * OK
   */
  200: Array<EnvironmentLockHistoryDto>;
};

export type GetLockHistoryByEnvironmentIdResponse = GetLockHistoryByEnvironmentIdResponses[keyof GetLockHistoryByEnvironmentIdResponses];

export type GetAllEnabledEnvironmentsData = {
  body?: never;
  path?: never;
  query?: never;
  url: '/api/environments/enabled';
};

export type GetAllEnabledEnvironmentsResponses = {
  /**
   * OK
   */
  200: Array<EnvironmentDto>;
};

export type GetAllEnabledEnvironmentsResponse = GetAllEnabledEnvironmentsResponses[keyof GetAllEnabledEnvironmentsResponses];

export type GetAllDeploymentsData = {
  body?: never;
  path?: never;
  query?: never;
  url: '/api/deployments';
};

export type GetAllDeploymentsResponses = {
  /**
   * OK
   */
  200: Array<DeploymentDto>;
};

export type GetAllDeploymentsResponse = GetAllDeploymentsResponses[keyof GetAllDeploymentsResponses];

export type GetDeploymentByIdData = {
  body?: never;
  path: {
    id: number;
  };
  query?: never;
  url: '/api/deployments/{id}';
};

export type GetDeploymentByIdResponses = {
  /**
   * OK
   */
  200: DeploymentDto;
};

export type GetDeploymentByIdResponse = GetDeploymentByIdResponses[keyof GetDeploymentByIdResponses];

export type GetDeploymentsByEnvironmentIdData = {
  body?: never;
  path: {
    environmentId: number;
  };
  query?: never;
  url: '/api/deployments/environment/{environmentId}';
};

export type GetDeploymentsByEnvironmentIdResponses = {
  /**
   * OK
   */
  200: Array<DeploymentDto>;
};

export type GetDeploymentsByEnvironmentIdResponse = GetDeploymentsByEnvironmentIdResponses[keyof GetDeploymentsByEnvironmentIdResponses];

export type GetLatestDeploymentByEnvironmentIdData = {
  body?: never;
  path: {
    environmentId: number;
  };
  query?: never;
  url: '/api/deployments/environment/{environmentId}/latest';
};

export type GetLatestDeploymentByEnvironmentIdResponses = {
  /**
   * OK
   */
  200: DeploymentDto;
};

export type GetLatestDeploymentByEnvironmentIdResponse = GetLatestDeploymentByEnvironmentIdResponses[keyof GetLatestDeploymentByEnvironmentIdResponses];

export type GetActivityHistoryByEnvironmentIdData = {
  body?: never;
  path: {
    environmentId: number;
  };
  query?: never;
  url: '/api/deployments/environment/{environmentId}/activity-history';
};

export type GetActivityHistoryByEnvironmentIdResponses = {
  /**
   * OK
   */
  200: Array<ActivityHistoryDto>;
};

export type GetActivityHistoryByEnvironmentIdResponse = GetActivityHistoryByEnvironmentIdResponses[keyof GetActivityHistoryByEnvironmentIdResponses];

export type GetCommitByRepositoryIdAndNameData = {
  body?: never;
  path: {
    repoId: number;
    sha: string;
  };
  query?: never;
  url: '/api/commits/repository/{repoId}/commit/{sha}';
};

export type GetCommitByRepositoryIdAndNameResponses = {
  /**
   * OK
   */
  200: CommitInfoDto;
};

export type GetCommitByRepositoryIdAndNameResponse = GetCommitByRepositoryIdAndNameResponses[keyof GetCommitByRepositoryIdAndNameResponses];

export type GetAllBranchesData = {
  body?: never;
  path?: never;
  query?: never;
  url: '/api/branches';
};

export type GetAllBranchesResponses = {
  /**
   * OK
   */
  200: Array<BranchInfoDto>;
};

export type GetAllBranchesResponse = GetAllBranchesResponses[keyof GetAllBranchesResponses];

export type GetBranchByRepositoryIdAndNameData = {
  body?: never;
  path: {
    repoId: number;
  };
  query: {
    name: string;
  };
  url: '/api/branches/repository/{repoId}/branch';
};

export type GetBranchByRepositoryIdAndNameResponses = {
  /**
   * OK
   */
  200: BranchDetailsDto;
};

export type GetBranchByRepositoryIdAndNameResponse = GetBranchByRepositoryIdAndNameResponses[keyof GetBranchByRepositoryIdAndNameResponses];

export type DeleteWorkflowGroupData = {
  body?: never;
  path: {
    repositoryId: number;
    groupId: number;
  };
  query?: never;
  url: '/api/settings/{repositoryId}/groups/{groupId}';
};

export type DeleteWorkflowGroupResponses = {
  /**
   * OK
   */
  200: unknown;
};
