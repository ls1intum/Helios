import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { QueryClient } from '@tanstack/angular-query-experimental';
import {
  getAllEnabledEnvironmentsQueryKey,
  getAllEnvironmentsQueryKey,
  getEnvironmentByIdQueryKey,
  getEnvironmentsByUserLockingQueryKey,
} from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { RepositoryService } from '@app/core/services/repository.service';
import { environment } from 'environments/environment';
import { Subscription, timer } from 'rxjs';
import { retry } from 'rxjs/operators';
import { WebSocketSubject, webSocket } from 'rxjs/webSocket';
import { EnvironmentDeploymentWsServerMessage } from './environment-deployment-websocket.types';

const SUBPROTOCOL = 'helios.v1';
const TOKEN_PREFIX = 'helios.token.';
const REPO_PREFIX = 'helios.repo.';
const INVALIDATION_DEBOUNCE_MS = 100;

@Injectable({ providedIn: 'root' })
export class EnvironmentDeploymentWebSocketService {
  private readonly keycloak = inject(KeycloakService);
  private readonly queryClient = inject(QueryClient);
  private readonly repositoryService = inject(RepositoryService);

  private socket$: WebSocketSubject<EnvironmentDeploymentWsServerMessage> | null = null;
  private socketSub: Subscription | null = null;
  private activeConsumers = signal(0);
  private activeRepositoryId: number | null = null;
  private connected = signal(false);
  private pendingEnvironmentIds = new Set<number>();
  private invalidationTimer: ReturnType<typeof setTimeout> | null = null;

  readonly isConnected = computed(() => this.connected());

  constructor() {
    effect(() => {
      const repositoryId = this.repositoryService.currentRepositoryId();
      const activeConsumers = this.activeConsumers();
      this.syncConnection(repositoryId, activeConsumers);
    });
  }

  activate(): () => void {
    this.activeConsumers.update(count => count + 1);
    return () => {
      this.activeConsumers.update(count => Math.max(0, count - 1));
    };
  }

  private syncConnection(repositoryIdValue: number | string | null, activeConsumers: number): void {
    const repositoryId = this.parseRepositoryId(repositoryIdValue);
    const shouldConnect = activeConsumers > 0 && repositoryId !== null;

    if (!shouldConnect) {
      this.disconnect();
      return;
    }

    if (this.socket$ && this.activeRepositoryId === repositoryId) {
      return;
    }

    this.disconnect();
    this.activeRepositoryId = repositoryId;
    this.socket$ = this.openSocket(repositoryId);
    this.socketSub = this.socket$
      .pipe(
        retry({
          delay: (_, attempt) => timer(Math.min(30_000, 500 * 2 ** Math.min(attempt, 6))),
        })
      )
      .subscribe({
        next: msg => this.handleMessage(msg),
        error: () => {
          this.connected.set(false);
          this.socket$ = null;
        },
      });
  }

  private openSocket(repositoryId: number): WebSocketSubject<EnvironmentDeploymentWsServerMessage> {
    const token = this.keycloak.keycloak.token ?? '';
    const protocols = [SUBPROTOCOL, `${TOKEN_PREFIX}${token}`, `${REPO_PREFIX}${repositoryId}`];
    return webSocket<EnvironmentDeploymentWsServerMessage>({
      url: this.buildUrl(),
      protocol: protocols,
      openObserver: {
        next: () => this.connected.set(true),
      },
      closeObserver: {
        next: () => this.connected.set(false),
      },
    });
  }

  private handleMessage(msg: EnvironmentDeploymentWsServerMessage): void {
    if (msg.type !== 'environment-deployment-invalidated') {
      return;
    }

    if (msg.repositoryId !== this.activeRepositoryId) {
      return;
    }

    this.pendingEnvironmentIds.add(msg.environmentId);
    if (this.invalidationTimer) {
      return;
    }

    this.invalidationTimer = setTimeout(() => this.flushInvalidations(), INVALIDATION_DEBOUNCE_MS);
  }

  private flushInvalidations(): void {
    const environmentIds = Array.from(this.pendingEnvironmentIds);
    this.pendingEnvironmentIds.clear();
    this.invalidationTimer = null;

    for (const environmentId of environmentIds) {
      this.queryClient.invalidateQueries({
        queryKey: getEnvironmentByIdQueryKey({ path: { id: environmentId } }),
      });
    }

    if (environmentIds.length === 0) {
      return;
    }

    this.queryClient.invalidateQueries({ queryKey: getAllEnabledEnvironmentsQueryKey() });
    this.queryClient.invalidateQueries({ queryKey: getAllEnvironmentsQueryKey() });
    this.queryClient.invalidateQueries({ queryKey: getEnvironmentsByUserLockingQueryKey() });
  }

  private disconnect(): void {
    if (this.invalidationTimer) {
      clearTimeout(this.invalidationTimer);
      this.invalidationTimer = null;
    }
    this.pendingEnvironmentIds.clear();
    this.socketSub?.unsubscribe();
    this.socketSub = null;
    this.socket$?.complete();
    this.socket$ = null;
    this.activeRepositoryId = null;
    this.connected.set(false);
  }

  private buildUrl(): string {
    const base = environment.serverUrl;
    const wsBase = base.startsWith('https://')
      ? 'wss://' + base.slice('https://'.length)
      : base.startsWith('http://')
        ? 'ws://' + base.slice('http://'.length)
        : base;
    return wsBase.replace(/\/+$/, '') + '/ws/environments';
  }

  private parseRepositoryId(repositoryId: number | string | null): number | null {
    if (repositoryId === null) {
      return null;
    }
    const parsed = Number(repositoryId);
    return Number.isFinite(parsed) ? parsed : null;
  }
}
