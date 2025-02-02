// This file is auto-generated by @hey-api/openapi-ts

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
    createdAt: {
      type: 'string',
      format: 'date-time',
    },
    updatedAt: {
      type: 'string',
      format: 'date-time',
    },
  },
  required: ['id', 'ref', 'sha', 'statusesUrl', 'task', 'url'],
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
    latestDeployment: {
      $ref: '#/components/schemas/EnvironmentDeployment',
    },
    lockedBy: {
      type: 'string',
    },
    lockedAt: {
      type: 'string',
      format: 'date-time',
    },
    type: {
      type: 'string',
      enum: ['TEST', 'STAGING', 'PRODUCTION'],
    },
  },
  required: ['id', 'name'],
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
    deploymentEnvironment: {
      type: 'string',
      enum: ['NONE', 'TEST_SERVER', 'STAGING_SERVER', 'PRODUCTION_SERVER'],
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
  required: ['deploymentEnvironment', 'id', 'name', 'path', 'state'],
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
      type: 'string',
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
    lockedBy: {
      type: 'string',
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
