// This file is auto-generated by @hey-api/openapi-ts

export const WorkflowGroupDtoSchema = {
  required: ['id', 'name', 'orderIndex'],
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
} as const;

export const WorkflowMembershipDtoSchema = {
  required: ['orderIndex', 'workflowId'],
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
} as const;

export const EnvironmentDeploymentSchema = {
  required: ['id', 'ref', 'sha', 'statusesUrl', 'task', 'url'],
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
      enum: ['PENDING', 'SUCCESS', 'ERROR', 'FAILURE', 'IN_PROGRESS', 'QUEUED', 'INACTIVE', 'UNKNOWN'],
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
} as const;

export const EnvironmentDtoSchema = {
  required: ['id', 'name'],
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
  },
} as const;

export const RepositoryInfoDtoSchema = {
  required: ['htmlUrl', 'id', 'name', 'nameWithOwner'],
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
} as const;

export const DeployRequestSchema = {
  required: ['branchName', 'environmentId'],
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
} as const;

export const WorkflowDtoSchema = {
  required: ['id', 'label', 'name', 'path', 'state'],
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
} as const;

export const WorkflowRunDtoSchema = {
  required: ['displayTitle', 'htmlUrl', 'id', 'name', 'status', 'workflowId'],
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
} as const;

export const LabelInfoDtoSchema = {
  required: ['color', 'id', 'name'],
  type: 'object',
  properties: {
    id: {
      type: 'integer',
      description: 'The unique identifier of the label',
      format: 'int64',
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
    },
  },
} as const;

export const PullRequestBaseInfoDtoSchema = {
  required: ['htmlUrl', 'id', 'isDraft', 'isMerged', 'number', 'state', 'title'],
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
} as const;

export const UserInfoDtoSchema = {
  required: ['avatarUrl', 'htmlUrl', 'id', 'login', 'name'],
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
} as const;

export const PullRequestInfoDtoSchema = {
  required: ['additions', 'commentsCount', 'deletions', 'headRefName', 'headRefRepoNameWithOwner', 'headSha', 'htmlUrl', 'id', 'isDraft', 'isMerged', 'number', 'state', 'title'],
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
} as const;

export const DeploymentDtoSchema = {
  required: ['environment', 'id', 'ref', 'sha', 'statusesUrl', 'task', 'url'],
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
      enum: ['PENDING', 'SUCCESS', 'ERROR', 'FAILURE', 'IN_PROGRESS', 'QUEUED', 'INACTIVE', 'UNKNOWN'],
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
} as const;

export const CommitInfoDtoSchema = {
  required: ['sha'],
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
} as const;

export const BranchInfoDtoSchema = {
  required: ['commitSha', 'name'],
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
} as const;
