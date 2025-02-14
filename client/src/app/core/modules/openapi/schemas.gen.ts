// This file is auto-generated by @hey-api/openapi-ts

export const GitRepoSettingsDtoSchema = {
  type: 'object',
  properties: {
    id: {
      type: 'integer',
      format: 'int64',
    },
    lockExpirationThreshold: {
      type: 'integer',
      format: 'int64',
    },
    lockReservationThreshold: {
      type: 'integer',
      format: 'int64',
    },
  },
} as const;

export const WorkflowGroupDtoSchema = {
  type: 'object',
  properties: {
    id: {
      type: 'integer',
      format: 'int64',
    },
    name: {
      type: 'string',
    },
    orderIndex: {
      type: 'integer',
      format: 'int32',
    },
    memberships: {
      type: 'array',
      items: {
        $ref: '#/components/schemas/WorkflowMembershipDto',
      },
    },
  },
  required: ['id', 'name', 'orderIndex'],
} as const;

export const WorkflowMembershipDtoSchema = {
  type: 'object',
  properties: {
    workflowId: {
      type: 'integer',
      format: 'int64',
    },
    orderIndex: {
      type: 'integer',
      format: 'int32',
    },
  },
  required: ['orderIndex', 'workflowId'],
} as const;

export const EnvironmentDeploymentSchema = {
  type: 'object',
  properties: {
    id: {
      type: 'integer',
      format: 'int64',
    },
    url: {
      type: 'string',
    },
    state: {
      type: 'string',
      enum: ['PENDING', 'WAITING', 'SUCCESS', 'ERROR', 'FAILURE', 'IN_PROGRESS', 'QUEUED', 'INACTIVE', 'UNKNOWN'],
    },
    statusesUrl: {
      type: 'string',
    },
    sha: {
      type: 'string',
    },
    ref: {
      type: 'string',
    },
    task: {
      type: 'string',
    },
    releaseCandidateName: {
      type: 'string',
    },
    user: {
      $ref: '#/components/schemas/UserInfoDto',
    },
    createdAt: {
      type: 'string',
      format: 'date-time',
    },
    updatedAt: {
      type: 'string',
      format: 'date-time',
    },
  },
  required: ['id'],
} as const;

export const EnvironmentDtoSchema = {
  type: 'object',
  properties: {
    repository: {
      $ref: '#/components/schemas/RepositoryInfoDto',
    },
    id: {
      type: 'integer',
      format: 'int64',
    },
    name: {
      type: 'string',
    },
    locked: {
      type: 'boolean',
    },
    url: {
      type: 'string',
    },
    htmlUrl: {
      type: 'string',
    },
    createdAt: {
      type: 'string',
      format: 'date-time',
    },
    updatedAt: {
      type: 'string',
      format: 'date-time',
    },
    enabled: {
      type: 'boolean',
    },
    installedApps: {
      type: 'array',
      items: {
        type: 'string',
      },
    },
    description: {
      type: 'string',
    },
    serverUrl: {
      type: 'string',
    },
    statusCheckType: {
      type: 'string',
      enum: ['HTTP_STATUS', 'ARTEMIS_INFO'],
    },
    statusUrl: {
      type: 'string',
    },
    latestDeployment: {
      $ref: '#/components/schemas/EnvironmentDeployment',
    },
    latestStatus: {
      $ref: '#/components/schemas/EnvironmentStatusDto',
    },
    lockedBy: {
      $ref: '#/components/schemas/UserInfoDto',
    },
    lockedAt: {
      type: 'string',
      format: 'date-time',
    },
    lockExpirationThreshold: {
      type: 'integer',
      format: 'int64',
    },
    lockReservationThreshold: {
      type: 'integer',
      format: 'int64',
    },
    lockWillExpireAt: {
      type: 'string',
      format: 'date-time',
    },
    lockReservationWillExpireAt: {
      type: 'string',
      format: 'date-time',
    },
  },
  required: ['id', 'name'],
} as const;

export const EnvironmentStatusDtoSchema = {
  type: 'object',
  properties: {
    id: {
      type: 'integer',
      format: 'int64',
    },
    success: {
      type: 'boolean',
    },
    httpStatusCode: {
      type: 'integer',
      format: 'int32',
    },
    checkedAt: {
      type: 'string',
      format: 'date-time',
    },
    checkType: {
      type: 'string',
      enum: ['HTTP_STATUS', 'ARTEMIS_INFO'],
    },
    metadata: {
      type: 'object',
      additionalProperties: {},
    },
  },
  required: ['checkType', 'checkedAt', 'httpStatusCode', 'id', 'success'],
} as const;

export const RepositoryInfoDtoSchema = {
  type: 'object',
  properties: {
    id: {
      type: 'integer',
      format: 'int64',
    },
    name: {
      type: 'string',
    },
    nameWithOwner: {
      type: 'string',
    },
    description: {
      type: 'string',
    },
    htmlUrl: {
      type: 'string',
    },
  },
  required: ['htmlUrl', 'id', 'name', 'nameWithOwner'],
} as const;

export const UserInfoDtoSchema = {
  type: 'object',
  properties: {
    id: {
      type: 'integer',
      format: 'int64',
    },
    login: {
      type: 'string',
    },
    avatarUrl: {
      type: 'string',
    },
    name: {
      type: 'string',
    },
    htmlUrl: {
      type: 'string',
    },
  },
  required: ['avatarUrl', 'htmlUrl', 'id', 'login', 'name'],
} as const;

export const ReleaseCandidateCreateDtoSchema = {
  type: 'object',
  properties: {
    name: {
      type: 'string',
    },
    commitSha: {
      type: 'string',
    },
    branchName: {
      type: 'string',
    },
  },
  required: ['commitSha', 'name'],
} as const;

export const ReleaseCandidateInfoDtoSchema = {
  type: 'object',
  properties: {
    name: {
      type: 'string',
    },
    commitSha: {
      type: 'string',
    },
    branchName: {
      type: 'string',
    },
  },
} as const;

export const DeployRequestSchema = {
  type: 'object',
  properties: {
    environmentId: {
      type: 'integer',
      format: 'int64',
    },
    branchName: {
      type: 'string',
    },
  },
  required: ['branchName', 'environmentId'],
} as const;

export const GitRepositorySchema = {
  type: 'object',
  properties: {
    repositoryId: {
      type: 'integer',
      format: 'int64',
    },
    createdAt: {
      type: 'string',
      format: 'date-time',
    },
    updatedAt: {
      type: 'string',
      format: 'date-time',
    },
    name: {
      type: 'string',
    },
    nameWithOwner: {
      type: 'string',
    },
    htmlUrl: {
      type: 'string',
    },
    description: {
      type: 'string',
    },
    homepage: {
      type: 'string',
    },
    pushedAt: {
      type: 'string',
      format: 'date-time',
    },
    visibility: {
      type: 'string',
      enum: ['PUBLIC', 'PRIVATE', 'INTERNAL', 'UNKNOWN'],
    },
    stargazersCount: {
      type: 'integer',
      format: 'int32',
    },
    watchersCount: {
      type: 'integer',
      format: 'int32',
    },
    defaultBranch: {
      type: 'string',
    },
    hasIssues: {
      type: 'boolean',
    },
    hasProjects: {
      type: 'boolean',
    },
    hasWiki: {
      type: 'boolean',
    },
    labels: {
      type: 'array',
      items: {
        $ref: '#/components/schemas/Label',
      },
      uniqueItems: true,
    },
    private: {
      type: 'boolean',
    },
    archived: {
      type: 'boolean',
    },
    disabled: {
      type: 'boolean',
    },
  },
  required: ['defaultBranch', 'htmlUrl', 'name', 'nameWithOwner', 'pushedAt', 'visibility'],
} as const;

export const IssueSchema = {
  type: 'object',
  properties: {
    repository: {
      $ref: '#/components/schemas/GitRepository',
    },
    id: {
      type: 'integer',
      format: 'int64',
    },
    createdAt: {
      type: 'string',
      format: 'date-time',
    },
    updatedAt: {
      type: 'string',
      format: 'date-time',
    },
    number: {
      type: 'integer',
      format: 'int32',
    },
    state: {
      type: 'string',
      enum: ['OPEN', 'CLOSED'],
    },
    title: {
      type: 'string',
    },
    body: {
      type: 'string',
    },
    htmlUrl: {
      type: 'string',
    },
    closedAt: {
      type: 'string',
      format: 'date-time',
    },
    commentsCount: {
      type: 'integer',
      format: 'int32',
    },
    author: {
      $ref: '#/components/schemas/User',
    },
    labels: {
      type: 'array',
      items: {
        $ref: '#/components/schemas/Label',
      },
      uniqueItems: true,
    },
    assignees: {
      type: 'array',
      items: {
        $ref: '#/components/schemas/User',
      },
      uniqueItems: true,
    },
    locked: {
      type: 'boolean',
    },
    pullRequest: {
      type: 'boolean',
    },
  },
  required: ['htmlUrl', 'state', 'title'],
} as const;

export const LabelSchema = {
  type: 'object',
  properties: {
    id: {
      type: 'integer',
      format: 'int64',
    },
    name: {
      type: 'string',
    },
    description: {
      type: 'string',
    },
    color: {
      type: 'string',
    },
    issues: {
      type: 'array',
      items: {
        $ref: '#/components/schemas/Issue',
      },
      uniqueItems: true,
    },
    repository: {
      $ref: '#/components/schemas/GitRepository',
    },
  },
} as const;

export const PullRequestSchema = {
  type: 'object',
  properties: {
    repository: {
      $ref: '#/components/schemas/GitRepository',
    },
    id: {
      type: 'integer',
      format: 'int64',
    },
    createdAt: {
      type: 'string',
      format: 'date-time',
    },
    updatedAt: {
      type: 'string',
      format: 'date-time',
    },
    number: {
      type: 'integer',
      format: 'int32',
    },
    state: {
      type: 'string',
      enum: ['OPEN', 'CLOSED'],
    },
    title: {
      type: 'string',
    },
    body: {
      type: 'string',
    },
    htmlUrl: {
      type: 'string',
    },
    closedAt: {
      type: 'string',
      format: 'date-time',
    },
    commentsCount: {
      type: 'integer',
      format: 'int32',
    },
    author: {
      $ref: '#/components/schemas/User',
    },
    labels: {
      type: 'array',
      items: {
        $ref: '#/components/schemas/Label',
      },
      uniqueItems: true,
    },
    assignees: {
      type: 'array',
      items: {
        $ref: '#/components/schemas/User',
      },
      uniqueItems: true,
    },
    mergedAt: {
      type: 'string',
      format: 'date-time',
    },
    mergeCommitSha: {
      type: 'string',
    },
    headRefName: {
      type: 'string',
    },
    headRefRepoNameWithOwner: {
      type: 'string',
    },
    headSha: {
      type: 'string',
    },
    isMergeable: {
      type: 'boolean',
    },
    mergeableState: {
      type: 'string',
    },
    maintainerCanModify: {
      type: 'boolean',
    },
    commits: {
      type: 'integer',
      format: 'int32',
    },
    additions: {
      type: 'integer',
      format: 'int32',
    },
    deletions: {
      type: 'integer',
      format: 'int32',
    },
    changedFiles: {
      type: 'integer',
      format: 'int32',
    },
    mergedBy: {
      $ref: '#/components/schemas/User',
    },
    requestedReviewers: {
      type: 'array',
      items: {
        $ref: '#/components/schemas/User',
      },
      uniqueItems: true,
    },
    workflowRuns: {
      type: 'array',
      items: {
        $ref: '#/components/schemas/WorkflowRun',
      },
      uniqueItems: true,
    },
    merged: {
      type: 'boolean',
    },
    pullRequest: {
      type: 'boolean',
    },
    draft: {
      type: 'boolean',
    },
    locked: {
      type: 'boolean',
    },
  },
  required: ['htmlUrl', 'state', 'title'],
} as const;

export const TestResultSchema = {
  type: 'object',
  properties: {
    id: {
      type: 'integer',
      format: 'int64',
    },
    workflowRun: {
      $ref: '#/components/schemas/WorkflowRun',
    },
    total: {
      type: 'integer',
      format: 'int32',
      minimum: 0,
    },
    passed: {
      type: 'integer',
      format: 'int32',
      minimum: 0,
    },
    failures: {
      type: 'integer',
      format: 'int32',
      minimum: 0,
    },
    errors: {
      type: 'integer',
      format: 'int32',
      minimum: 0,
    },
    skipped: {
      type: 'integer',
      format: 'int32',
      minimum: 0,
    },
  },
} as const;

export const UserSchema = {
  type: 'object',
  properties: {
    repository: {
      $ref: '#/components/schemas/GitRepository',
    },
    id: {
      type: 'integer',
      format: 'int64',
    },
    createdAt: {
      type: 'string',
      format: 'date-time',
    },
    updatedAt: {
      type: 'string',
      format: 'date-time',
    },
    login: {
      type: 'string',
    },
    avatarUrl: {
      type: 'string',
    },
    description: {
      type: 'string',
    },
    name: {
      type: 'string',
    },
    company: {
      type: 'string',
    },
    blog: {
      type: 'string',
    },
    location: {
      type: 'string',
    },
    email: {
      type: 'string',
    },
    htmlUrl: {
      type: 'string',
    },
    type: {
      type: 'string',
      enum: ['USER', 'ORGANIZATION', 'BOT'],
    },
    followers: {
      type: 'integer',
      format: 'int32',
    },
    following: {
      type: 'integer',
      format: 'int32',
    },
  },
  required: ['avatarUrl', 'htmlUrl', 'login', 'name', 'type'],
} as const;

export const WorkflowRunSchema = {
  type: 'object',
  properties: {
    repository: {
      $ref: '#/components/schemas/GitRepository',
    },
    id: {
      type: 'integer',
      format: 'int64',
    },
    createdAt: {
      type: 'string',
      format: 'date-time',
    },
    updatedAt: {
      type: 'string',
      format: 'date-time',
    },
    pullRequests: {
      type: 'array',
      items: {
        $ref: '#/components/schemas/PullRequest',
      },
      uniqueItems: true,
    },
    name: {
      type: 'string',
    },
    displayTitle: {
      type: 'string',
    },
    runNumber: {
      type: 'integer',
      format: 'int64',
    },
    workflowId: {
      type: 'integer',
      format: 'int64',
    },
    runAttempt: {
      type: 'integer',
      format: 'int64',
    },
    runStartedAt: {
      type: 'string',
      format: 'date-time',
    },
    htmlUrl: {
      type: 'string',
    },
    jobsUrl: {
      type: 'string',
    },
    logsUrl: {
      type: 'string',
    },
    checkSuiteUrl: {
      type: 'string',
    },
    artifactsUrl: {
      type: 'string',
    },
    cancelUrl: {
      type: 'string',
    },
    rerunUrl: {
      type: 'string',
    },
    workflowUrl: {
      type: 'string',
    },
    headBranch: {
      type: 'string',
    },
    headSha: {
      type: 'string',
    },
    status: {
      type: 'string',
      enum: [
        'QUEUED',
        'IN_PROGRESS',
        'COMPLETED',
        'ACTION_REQUIRED',
        'CANCELLED',
        'FAILURE',
        'NEUTRAL',
        'SKIPPED',
        'STALE',
        'SUCCESS',
        'TIMED_OUT',
        'REQUESTED',
        'WAITING',
        'PENDING',
        'UNKNOWN',
      ],
    },
    conclusion: {
      type: 'string',
      enum: ['ACTION_REQUIRED', 'CANCELLED', 'FAILURE', 'NEUTRAL', 'SUCCESS', 'SKIPPED', 'STALE', 'TIMED_OUT', 'STARTUP_FAILURE', 'UNKNOWN'],
    },
  },
} as const;

export const WorkflowDtoSchema = {
  type: 'object',
  properties: {
    id: {
      type: 'integer',
      format: 'int64',
    },
    repository: {
      $ref: '#/components/schemas/RepositoryInfoDto',
    },
    name: {
      type: 'string',
    },
    path: {
      type: 'string',
    },
    fileNameWithExtension: {
      type: 'string',
    },
    state: {
      type: 'string',
      enum: ['ACTIVE', 'DELETED', 'DISABLED_FORK', 'DISABLED_INACTIVITY', 'DISABLED_MANUALLY', 'UNKNOWN'],
    },
    url: {
      type: 'string',
    },
    htmlUrl: {
      type: 'string',
    },
    badgeUrl: {
      type: 'string',
    },
    label: {
      type: 'string',
      enum: ['BUILD', 'DEPLOYMENT', 'NONE'],
    },
    createdAt: {
      type: 'string',
      format: 'date-time',
    },
    updatedAt: {
      type: 'string',
      format: 'date-time',
    },
  },
  required: ['id', 'label', 'name', 'path', 'state'],
} as const;

export const WorkflowRunDtoSchema = {
  type: 'object',
  properties: {
    id: {
      type: 'integer',
      format: 'int64',
    },
    name: {
      type: 'string',
    },
    displayTitle: {
      type: 'string',
    },
    status: {
      type: 'string',
      enum: [
        'QUEUED',
        'IN_PROGRESS',
        'COMPLETED',
        'ACTION_REQUIRED',
        'CANCELLED',
        'FAILURE',
        'NEUTRAL',
        'SKIPPED',
        'STALE',
        'SUCCESS',
        'TIMED_OUT',
        'REQUESTED',
        'WAITING',
        'PENDING',
        'UNKNOWN',
      ],
    },
    workflowId: {
      type: 'integer',
      format: 'int64',
    },
    conclusion: {
      type: 'string',
      enum: ['ACTION_REQUIRED', 'CANCELLED', 'FAILURE', 'NEUTRAL', 'SUCCESS', 'SKIPPED', 'STALE', 'TIMED_OUT', 'STARTUP_FAILURE', 'UNKNOWN'],
    },
    htmlUrl: {
      type: 'string',
    },
  },
  required: ['displayTitle', 'htmlUrl', 'id', 'name', 'status', 'workflowId'],
} as const;

export const GitHubRepositoryRoleDtoSchema = {
  type: 'object',
  properties: {
    permission: {
      type: 'string',
      enum: ['ADMIN', 'WRITE', 'READ', 'NONE'],
    },
    roleName: {
      type: 'string',
    },
  },
} as const;

export const BranchInfoDtoSchema = {
  type: 'object',
  properties: {
    name: {
      type: 'string',
    },
    commitSha: {
      type: 'string',
    },
    aheadBy: {
      type: 'integer',
      format: 'int32',
    },
    behindBy: {
      type: 'integer',
      format: 'int32',
    },
    isDefault: {
      type: 'boolean',
    },
    isProtected: {
      type: 'boolean',
    },
    updatedAt: {
      type: 'string',
      format: 'date-time',
    },
    updatedBy: {
      $ref: '#/components/schemas/UserInfoDto',
    },
    repository: {
      $ref: '#/components/schemas/RepositoryInfoDto',
    },
  },
  required: ['commitSha', 'name'],
} as const;

export const CommitInfoDtoSchema = {
  type: 'object',
  properties: {
    sha: {
      type: 'string',
    },
    author: {
      $ref: '#/components/schemas/UserInfoDto',
    },
    message: {
      type: 'string',
    },
    authoredAt: {
      type: 'string',
      format: 'date-time',
    },
    repository: {
      $ref: '#/components/schemas/RepositoryInfoDto',
    },
  },
  required: ['sha'],
} as const;

export const DeploymentDtoSchema = {
  type: 'object',
  properties: {
    id: {
      type: 'integer',
      format: 'int64',
    },
    repository: {
      $ref: '#/components/schemas/RepositoryInfoDto',
    },
    url: {
      type: 'string',
    },
    state: {
      type: 'string',
      enum: ['PENDING', 'WAITING', 'SUCCESS', 'ERROR', 'FAILURE', 'IN_PROGRESS', 'QUEUED', 'INACTIVE', 'UNKNOWN'],
    },
    statusesUrl: {
      type: 'string',
    },
    sha: {
      type: 'string',
    },
    ref: {
      type: 'string',
    },
    task: {
      type: 'string',
    },
    environment: {
      $ref: '#/components/schemas/EnvironmentDto',
    },
    user: {
      $ref: '#/components/schemas/UserInfoDto',
    },
    createdAt: {
      type: 'string',
      format: 'date-time',
    },
    updatedAt: {
      type: 'string',
      format: 'date-time',
    },
  },
  required: ['environment', 'id', 'ref', 'sha', 'statusesUrl', 'task', 'url'],
} as const;

export const ReleaseCandidateDetailsDtoSchema = {
  type: 'object',
  properties: {
    name: {
      type: 'string',
    },
    commit: {
      $ref: '#/components/schemas/CommitInfoDto',
    },
    branch: {
      $ref: '#/components/schemas/BranchInfoDto',
    },
    deployments: {
      type: 'array',
      items: {
        $ref: '#/components/schemas/DeploymentDto',
      },
    },
    evaluations: {
      type: 'array',
      items: {
        $ref: '#/components/schemas/ReleaseCandidateEvaluationDto',
      },
    },
    createdBy: {
      $ref: '#/components/schemas/UserInfoDto',
    },
    createdAt: {
      type: 'string',
      format: 'date-time',
    },
  },
  required: ['commit', 'createdAt', 'createdBy', 'deployments', 'evaluations', 'name'],
} as const;

export const ReleaseCandidateEvaluationDtoSchema = {
  type: 'object',
  properties: {
    user: {
      $ref: '#/components/schemas/UserInfoDto',
    },
    isWorking: {
      type: 'boolean',
    },
  },
  required: ['isWorking', 'user'],
} as const;

export const CommitsSinceReleaseCandidateDtoSchema = {
  type: 'object',
  properties: {
    commitsLength: {
      type: 'integer',
      format: 'int32',
    },
    commits: {
      type: 'array',
      items: {
        $ref: '#/components/schemas/CommitInfoDto',
      },
    },
  },
  required: ['commits', 'commitsLength'],
} as const;

export const LabelInfoDtoSchema = {
  type: 'object',
  properties: {
    id: {
      type: 'integer',
      format: 'int64',
      description: 'The unique identifier of the label',
    },
    name: {
      type: 'string',
      description: 'The name of the label',
      example: 'bug',
    },
    color: {
      type: 'string',
      description: 'The color of the label as a 6-character hex code (without #)',
      example: 'ff0000',
    },
    repository: {
      $ref: '#/components/schemas/RepositoryInfoDto',
      description: 'The repository associated with this label',
    },
  },
  required: ['color', 'id', 'name'],
} as const;

export const PullRequestBaseInfoDtoSchema = {
  type: 'object',
  properties: {
    id: {
      type: 'integer',
      format: 'int64',
    },
    number: {
      type: 'integer',
      format: 'int32',
    },
    title: {
      type: 'string',
    },
    state: {
      type: 'string',
      enum: ['OPEN', 'CLOSED'],
    },
    isDraft: {
      type: 'boolean',
    },
    isMerged: {
      type: 'boolean',
    },
    repository: {
      $ref: '#/components/schemas/RepositoryInfoDto',
    },
    htmlUrl: {
      type: 'string',
    },
    createdAt: {
      type: 'string',
      format: 'date-time',
    },
    updatedAt: {
      type: 'string',
      format: 'date-time',
    },
    author: {
      $ref: '#/components/schemas/UserInfoDto',
    },
    labels: {
      type: 'array',
      items: {
        $ref: '#/components/schemas/LabelInfoDto',
      },
    },
    assignees: {
      type: 'array',
      items: {
        $ref: '#/components/schemas/UserInfoDto',
      },
    },
    reviewers: {
      type: 'array',
      items: {
        $ref: '#/components/schemas/UserInfoDto',
      },
    },
  },
  required: ['htmlUrl', 'id', 'isDraft', 'isMerged', 'number', 'state', 'title'],
} as const;

export const PullRequestInfoDtoSchema = {
  type: 'object',
  properties: {
    id: {
      type: 'integer',
      format: 'int64',
    },
    number: {
      type: 'integer',
      format: 'int32',
    },
    title: {
      type: 'string',
    },
    state: {
      type: 'string',
      enum: ['OPEN', 'CLOSED'],
    },
    isDraft: {
      type: 'boolean',
    },
    isMerged: {
      type: 'boolean',
    },
    commentsCount: {
      type: 'integer',
      format: 'int32',
    },
    author: {
      $ref: '#/components/schemas/UserInfoDto',
    },
    labels: {
      type: 'array',
      items: {
        $ref: '#/components/schemas/LabelInfoDto',
      },
    },
    assignees: {
      type: 'array',
      items: {
        $ref: '#/components/schemas/UserInfoDto',
      },
    },
    repository: {
      $ref: '#/components/schemas/RepositoryInfoDto',
    },
    additions: {
      type: 'integer',
      format: 'int32',
    },
    deletions: {
      type: 'integer',
      format: 'int32',
    },
    headSha: {
      type: 'string',
    },
    headRefName: {
      type: 'string',
    },
    headRefRepoNameWithOwner: {
      type: 'string',
    },
    mergedAt: {
      type: 'string',
      format: 'date-time',
    },
    closedAt: {
      type: 'string',
      format: 'date-time',
    },
    htmlUrl: {
      type: 'string',
    },
    createdAt: {
      type: 'string',
      format: 'date-time',
    },
    updatedAt: {
      type: 'string',
      format: 'date-time',
    },
  },
  required: ['additions', 'commentsCount', 'deletions', 'headRefName', 'headRefRepoNameWithOwner', 'headSha', 'htmlUrl', 'id', 'isDraft', 'isMerged', 'number', 'state', 'title'],
} as const;

export const EnvironmentLockHistoryDtoSchema = {
  type: 'object',
  properties: {
    id: {
      type: 'integer',
      format: 'int64',
    },
    lockedBy: {
      $ref: '#/components/schemas/UserInfoDto',
    },
    unlockedBy: {
      $ref: '#/components/schemas/UserInfoDto',
    },
    lockedAt: {
      type: 'string',
      format: 'date-time',
    },
    unlockedAt: {
      type: 'string',
      format: 'date-time',
    },
    environment: {
      $ref: '#/components/schemas/EnvironmentDto',
    },
  },
  required: ['id'],
} as const;

export const ActivityHistoryDtoSchema = {
  type: 'object',
  properties: {
    type: {
      type: 'string',
    },
    id: {
      type: 'integer',
      format: 'int64',
    },
    repository: {
      $ref: '#/components/schemas/RepositoryInfoDto',
    },
    state: {
      type: 'string',
      enum: ['PENDING', 'WAITING', 'SUCCESS', 'ERROR', 'FAILURE', 'IN_PROGRESS', 'QUEUED', 'INACTIVE', 'UNKNOWN'],
    },
    sha: {
      type: 'string',
    },
    ref: {
      type: 'string',
    },
    user: {
      $ref: '#/components/schemas/UserInfoDto',
    },
    user2: {
      $ref: '#/components/schemas/UserInfoDto',
    },
    timestamp: {
      type: 'string',
      format: 'date-time',
    },
    createdAt: {
      type: 'string',
      format: 'date-time',
    },
    updatedAt: {
      type: 'string',
      format: 'date-time',
    },
  },
} as const;

export const BranchDetailsDtoSchema = {
  type: 'object',
  properties: {
    name: {
      type: 'string',
    },
    commitSha: {
      type: 'string',
    },
    aheadBy: {
      type: 'integer',
      format: 'int32',
    },
    behindBy: {
      type: 'integer',
      format: 'int32',
    },
    isDefault: {
      type: 'boolean',
    },
    isProtected: {
      type: 'boolean',
    },
    releaseCandidateName: {
      type: 'string',
    },
    updatedAt: {
      type: 'string',
      format: 'date-time',
    },
    updatedBy: {
      $ref: '#/components/schemas/UserInfoDto',
    },
    repository: {
      $ref: '#/components/schemas/RepositoryInfoDto',
    },
  },
  required: ['commitSha', 'name'],
} as const;
