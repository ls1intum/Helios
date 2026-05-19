/**
 * Thin queue-API client. Real codebase practice is to use auto-generated TanStack hooks via
 * `npm run generate:openapi`, which can't be run from inside this implementation pass. Once the
 * server endpoints are regenerated, replace these manual fetchers with the generated `*Options`
 * helpers. The interfaces match the server DTOs in `QueueDtos.java`.
 */
import { HttpClient } from '@angular/common/http';
import { inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

export interface LabelSetDepth {
  labels: string[];
  queued: number;
  inProgress: number;
  oldestQueuedSeconds: number | null;
  runnerKind: string | null;
}

export interface QueueDepth {
  labelSets: LabelSetDepth[];
  totalQueued: number;
  totalInProgress: number;
}

export interface QueuedJob {
  jobId: number;
  runId: number;
  workflowName: string;
  jobName: string;
  headBranch: string;
  labels: string[];
  waitSeconds: number | null;
  etaSeconds: number | null;
  positionInQueue: number;
  queuedReason: string | null;
  isStuck: boolean;
  runnerKind: string | null;
}

export interface TrendPoint {
  bucket: string;
  queueP50: number | null;
  runP50: number | null;
}

export interface QueueStats {
  samples: number;
  queueP50: number | null;
  queueP90: number | null;
  queueP95: number | null;
  runP50: number | null;
  runP90: number | null;
  runP95: number | null;
  trend: TrendPoint[];
}

export interface RunnerDto {
  id: number;
  name: string;
  os: string;
  status: 'ONLINE' | 'OFFLINE';
  busy: boolean;
  labels: string[];
  runnerGroupId: number | null;
  runnerGroupName: string | null;
  currentJobId: number | null;
  lastSeenAt: string;
  offlineSince: string | null;
}

export interface RunnerPool {
  labels: string[];
  online: number;
  busy: number;
  idle: number;
  offline: number;
}

export interface AlertRuleDto {
  id: number | null;
  kind: 'QUEUE_P95_OVER' | 'RUNNER_OFFLINE_OVER' | 'STUCK_JOBS_OVER';
  thresholdSeconds: number | null;
  windowMinutes: number | null;
  repositoryId: number | null;
  labelSetHash: string | null;
  channels: string[] | null;
  enabled: boolean;
  quietWindow: string | null;
}

export interface AlertEventDto {
  id: number;
  ruleId: number;
  repositoryId: number | null;
  firedAt: string;
  clearedAt: string | null;
  measuredValue: number | null;
  details: string | null;
}

export function queueApi() {
  const http = inject(HttpClient);
  return {
    depth: (repoId: number) => firstValueFrom(http.get<QueueDepth>(`/api/queue/repos/${repoId}/depth`)),
    jobs: (repoId: number, status: string, limit = 100) =>
      firstValueFrom(http.get<QueuedJob[]>(`/api/queue/repos/${repoId}/jobs?status=${encodeURIComponent(status)}&limit=${limit}`)),
    stats: (repoId: number, params: { workflow?: string; job?: string; branch?: string; window?: '7d' | '30d' } = {}) => {
      const q = new URLSearchParams();
      if (params.workflow) q.set('workflow', params.workflow);
      if (params.job) q.set('job', params.job);
      if (params.branch) q.set('branch', params.branch);
      if (params.window) q.set('window', params.window);
      return firstValueFrom(http.get<QueueStats>(`/api/queue/repos/${repoId}/stats?${q.toString()}`));
    },
    orgDepth: () => firstValueFrom(http.get<QueueDepth>(`/api/queue/org/depth`)),
    listRules: (repoId: number) => firstValueFrom(http.get<AlertRuleDto[]>(`/api/queue/repos/${repoId}/alerts/rules`)),
    createRule: (repoId: number, body: AlertRuleDto) => firstValueFrom(http.post<AlertRuleDto>(`/api/queue/repos/${repoId}/alerts/rules`, body)),
    updateRule: (repoId: number, id: number, body: AlertRuleDto) => firstValueFrom(http.put<AlertRuleDto>(`/api/queue/repos/${repoId}/alerts/rules/${id}`, body)),
    deleteRule: (repoId: number, id: number) => firstValueFrom(http.delete<void>(`/api/queue/repos/${repoId}/alerts/rules/${id}`)),
    events: (repoId: number, hoursBack = 24) => firstValueFrom(http.get<AlertEventDto[]>(`/api/queue/repos/${repoId}/alerts/events?hoursBack=${hoursBack}`)),
  };
}

export function runnerApi() {
  const http = inject(HttpClient);
  return {
    list: () => firstValueFrom(http.get<RunnerDto[]>('/api/runners')),
    pools: () => firstValueFrom(http.get<RunnerPool[]>('/api/runners/pools')),
    byId: (id: number) => firstValueFrom(http.get<RunnerDto>(`/api/runners/${id}`)),
  };
}
