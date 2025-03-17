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
      enum: ['REQUESTED', 'PENDING', 'WAITING', 'SUCCESS', 'ERROR', 'FAILURE', 'IN_PROGRESS', 'QUEUED', 'INACTIVE', 'UNKNOWN'],
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
    workflowRunHtmlUrl: {
      type: 'string',
    },
    releaseCandidateName: {
      type: 'string',
    },
    prName: {
      type: 'string',
    },
    user: {
      $ref: '#/components/schemas/UserInfoDto',
    },
    pullRequestNumber: {
      type: 'integer',
      format: 'int32',
    },
    createdAt: {
      type: 'string',
      format: 'date-time',
    },
    updatedAt: {
      type: 'string',
      format: 'date-time',
    },
    type: {
      type: 'string',
      enum: ['GITHUB', 'HELIOS'],
    },
  },
  required: ['id', 'type'],
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
    type: {
      type: 'string',
      enum: ['TEST', 'STAGING', 'PRODUCTION'],
    },
    deploymentWorkflow: {
      $ref: '#/components/schemas/WorkflowDto',
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
      additionalProperties: {
        type: 'object',
      },
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
      enum: ['NONE', 'TEST'],
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
  required: ['branchName', 'commitSha', 'name'],
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
    commitSha: {
      type: 'string',
    },
  },
  required: ['branchName', 'environmentId'],
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
    label: {
      type: 'string',
      enum: ['NONE', 'TEST'],
    },
    testProcessingStatus: {
      type: 'string',
      enum: ['PROCESSING', 'PROCESSED', 'FAILED'],
    },
  },
  required: ['displayTitle', 'htmlUrl', 'id', 'label', 'name', 'status', 'workflowId'],
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

export const TestCaseDtoSchema = {
  type: 'object',
  properties: {
    id: {
      type: 'integer',
      format: 'int64',
    },
    name: {
      type: 'string',
    },
    className: {
      type: 'string',
    },
    status: {
      type: 'string',
      enum: ['PASSED', 'FAILED', 'ERROR', 'SKIPPED'],
    },
    previousStatus: {
      type: 'string',
      enum: ['PASSED', 'FAILED', 'ERROR', 'SKIPPED'],
    },
    time: {
      type: 'number',
      format: 'double',
    },
    message: {
      type: 'string',
    },
    stackTrace: {
      type: 'string',
    },
    errorType: {
      type: 'string',
    },
  },
  required: ['className', 'id', 'name', 'status', 'time'],
} as const;

export const TestResultsDtoSchema = {
  type: 'object',
  properties: {
    testSuites: {
      type: 'array',
      items: {
        $ref: '#/components/schemas/TestSuiteDto',
      },
    },
    isProcessing: {
      type: 'boolean',
    },
  },
  required: ['testSuites'],
} as const;

export const TestSuiteDtoSchema = {
  type: 'object',
  properties: {
    id: {
      type: 'integer',
      format: 'int64',
    },
    name: {
      type: 'string',
    },
    timestamp: {
      type: 'string',
      format: 'date-time',
    },
    tests: {
      type: 'integer',
      format: 'int32',
    },
    failures: {
      type: 'integer',
      format: 'int32',
    },
    errors: {
      type: 'integer',
      format: 'int32',
    },
    skipped: {
      type: 'integer',
      format: 'int32',
    },
    time: {
      type: 'number',
      format: 'double',
    },
    testCases: {
      type: 'array',
      items: {
        $ref: '#/components/schemas/TestCaseDto',
      },
    },
    workflowId: {
      type: 'integer',
      format: 'int64',
    },
    workflowName: {
      type: 'string',
    },
  },
  required: ['errors', 'failures', 'id', 'name', 'skipped', 'testCases', 'tests', 'time', 'timestamp'],
} as const;

export const GroupedTestResultsDtoSchema = {
  type: 'object',
  properties: {
    testResults: {
      type: 'object',
      additionalProperties: {
        $ref: '#/components/schemas/WorkflowTestResults',
      },
    },
    isProcessing: {
      type: 'boolean',
    },
  },
  required: ['testResults'],
} as const;

export const WorkflowTestResultsSchema = {
  type: 'object',
  properties: {
    workflowId: {
      type: 'integer',
      format: 'int64',
    },
    workflowName: {
      type: 'string',
    },
    testSuites: {
      type: 'array',
      items: {
        $ref: '#/components/schemas/TestSuiteDto',
      },
    },
    isProcessing: {
      type: 'boolean',
    },
  },
  required: ['testSuites', 'workflowId', 'workflowName'],
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
    isPinned: {
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

export const ReleaseCandidateDeploymentDtoSchema = {
  type: 'object',
  properties: {
    id: {
      type: 'integer',
      format: 'int64',
    },
    type: {
      type: 'string',
      enum: ['GITHUB', 'HELIOS'],
    },
    environmentId: {
      type: 'integer',
      format: 'int64',
    },
  },
  required: ['environmentId', 'id', 'type'],
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
        $ref: '#/components/schemas/ReleaseCandidateDeploymentDto',
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
  required: ['branch', 'commit', 'createdAt', 'createdBy', 'deployments', 'evaluations', 'name'],
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
    aheadBy: {
      type: 'integer',
      format: 'int32',
    },
    behindBy: {
      type: 'integer',
      format: 'int32',
    },
    commits: {
      type: 'array',
      items: {
        $ref: '#/components/schemas/CompareCommitInfoDto',
      },
    },
    compareUrl: {
      type: 'string',
    },
  },
  required: ['aheadBy', 'behindBy', 'commits'],
} as const;

export const CompareCommitInfoDtoSchema = {
  type: 'object',
  properties: {
    sha: {
      type: 'string',
    },
    message: {
      type: 'string',
    },
    authorName: {
      type: 'string',
    },
    authorEmail: {
      type: 'string',
    },
    url: {
      type: 'string',
    },
  },
  required: ['authorEmail', 'authorName', 'message', 'sha', 'url'],
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
    isPinned: {
      type: 'boolean',
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
