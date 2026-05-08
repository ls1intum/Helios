import { Injectable, inject } from '@angular/core';
import { defer, Observable, Subject, Subscription, timer } from 'rxjs';
import { filter, repeat, retry } from 'rxjs/operators';
import { WebSocketSubject, webSocket } from 'rxjs/webSocket';
import { environment } from 'environments/environment';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { WsClientMessage, WsServerMessage } from './workflow-run-websocket.types';

const SUBPROTOCOL = 'helios.v1';
const TOKEN_PREFIX = 'helios.token.';
const REPO_PREFIX = 'helios.repo.';

/**
 * Shared, refcounted WebSocket connection for workflow run push updates.
 *
 * One socket per app, multiplexed by runId. The socket opens lazily on the first {@link subscribe}
 * call and closes when the last subscription is torn down. Reconnect uses exponential backoff and
 * re-emits subscribe envelopes for every active runId.
 */
@Injectable({ providedIn: 'root' })
export class WorkflowRunWebSocketService {
  private readonly keycloak = inject(KeycloakService);

  private socket$: WebSocketSubject<WsServerMessage> | null = null;
  private socketSub: Subscription | null = null;
  private readonly incoming$ = new Subject<WsServerMessage>();
  private readonly activeRunIds = new Map<number, number>();

  /**
   * Subscribe to push updates for a single workflow run. Multiple callers for the same runId share
   * the same upstream subscription; the socket sends `{type:'subscribe'}` once and `{type:'unsubscribe'}`
   * after the last caller tears down.
   */
  subscribe(runId: number, repositoryId: number): Observable<WsServerMessage> {
    return new Observable<WsServerMessage>(subscriber => {
      this.ensureConnected(repositoryId);
      const refcount = this.activeRunIds.get(runId) ?? 0;
      this.activeRunIds.set(runId, refcount + 1);
      if (refcount === 0) {
        this.send({ type: 'subscribe', runId });
      }

      const sub = this.incoming$.pipe(filter(msg => 'runId' in msg && msg.runId === runId)).subscribe(subscriber);

      return () => {
        sub.unsubscribe();
        const next = (this.activeRunIds.get(runId) ?? 1) - 1;
        if (next <= 0) {
          this.activeRunIds.delete(runId);
          this.send({ type: 'unsubscribe', runId });
        } else {
          this.activeRunIds.set(runId, next);
        }
        if (this.activeRunIds.size === 0) {
          this.disconnect();
        }
      };
    });
  }

  private ensureConnected(repositoryId: number): void {
    if (this.socket$) {
      return;
    }
    this.socketSub = defer(() => {
      this.socket$ = this.openSocket(repositoryId);
      return this.socket$;
    })
      .pipe(
        retry({
          delay: (_, attempt) => this.reconnectDelay(attempt),
        }),
        repeat({
          delay: attempt => this.reconnectDelay(attempt),
        }),
      )
      .subscribe({
        next: msg => this.incoming$.next(msg),
        error: () => {
          // retry already exhausted somehow; close out
          this.socket$ = null;
        },
      });
  }

  private openSocket(repositoryId: number): WebSocketSubject<WsServerMessage> {
    const token = this.keycloak.keycloak.token ?? '';
    const protocols = [SUBPROTOCOL, `${TOKEN_PREFIX}${token}`, `${REPO_PREFIX}${repositoryId}`];
    return webSocket<WsServerMessage>({
      url: this.buildUrl(),
      protocol: protocols,
      openObserver: {
        next: () => {
          // On (re)connect, re-emit subscribe envelopes for every active runId.
          for (const runId of this.activeRunIds.keys()) {
            this.send({ type: 'subscribe', runId });
          }
        },
      },
    });
  }

  private send(msg: WsClientMessage): void {
    this.socket$?.next(msg as unknown as WsServerMessage);
  }

  private reconnectDelay(attempt: number): Observable<number> {
    return timer(Math.min(30_000, 500 * 2 ** Math.min(attempt, 6)));
  }

  private disconnect(): void {
    this.socketSub?.unsubscribe();
    this.socketSub = null;
    this.socket$?.complete();
    this.socket$ = null;
  }

  private buildUrl(): string {
    const base = environment.serverUrl;
    const wsBase = base.startsWith('https://') ? 'wss://' + base.slice('https://'.length) : base.startsWith('http://') ? 'ws://' + base.slice('http://'.length) : base;
    return wsBase.replace(/\/+$/, '') + '/ws/workflow-runs';
  }
}
