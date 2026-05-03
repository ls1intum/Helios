import { WorkflowRunDto } from '@app/core/modules/openapi';

export type WsClientMessage =
  | { type: 'subscribe'; runId: number }
  | { type: 'unsubscribe'; runId: number }
  | { type: 'ping' };

export type WsServerMessage =
  | { type: 'workflow-run-updated'; runId: number; run: WorkflowRunDto }
  | { type: 'workflow-jobs-invalidated'; runId: number }
  | { type: 'error'; code: string; message: string; runId?: number | null }
  | { type: 'pong' };
