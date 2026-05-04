import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { Subject } from 'rxjs';
import { QueryClient } from '@tanstack/angular-query-experimental';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { RepositoryService } from '@app/core/services/repository.service';
import { EnvironmentDeploymentWebSocketService } from './environment-deployment-websocket.service';
import { EnvironmentDeploymentWsServerMessage } from './environment-deployment-websocket.types';
import { webSocket } from 'rxjs/webSocket';

vi.mock('rxjs/webSocket', () => ({
  webSocket: vi.fn(),
}));

describe('EnvironmentDeploymentWebSocketService', () => {
  let socketSubject: Subject<EnvironmentDeploymentWsServerMessage>;
  let queryClient: { invalidateQueries: ReturnType<typeof vi.fn> };
  let repositoryService: { currentRepositoryId: ReturnType<typeof signal<number | string | null>> };

  beforeEach(() => {
    vi.useFakeTimers();
    socketSubject = new Subject<EnvironmentDeploymentWsServerMessage>();
    queryClient = { invalidateQueries: vi.fn() };
    repositoryService = { currentRepositoryId: signal<number | string | null>(null) };
    vi.mocked(webSocket).mockReturnValue(socketSubject as never);

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        EnvironmentDeploymentWebSocketService,
        {
          provide: QueryClient,
          useValue: queryClient,
        },
        {
          provide: KeycloakService,
          useValue: {
            keycloak: {
              token: 'access-token',
            },
          },
        },
        {
          provide: RepositoryService,
          useValue: repositoryService,
        },
      ],
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  it('opens the repository-scoped WebSocket with token and repository subprotocols', () => {
    const service = TestBed.inject(EnvironmentDeploymentWebSocketService);

    service.activate();
    repositoryService.currentRepositoryId.set(42);
    TestBed.flushEffects();

    expect(webSocket).toHaveBeenCalledOnce();
    const config = vi.mocked(webSocket).mock.calls[0][0];
    expect(config.url).toMatch(/\/ws\/environments$/);
    expect(config.protocol).toEqual([
      'helios.v1',
      'helios.token.access-token',
      'helios.repo.42',
    ]);

    config.openObserver?.next(undefined);
    expect(service.isConnected()).toBe(true);
  });

  it('batches environment deployment invalidations', () => {
    TestBed.inject(EnvironmentDeploymentWebSocketService).activate();
    repositoryService.currentRepositoryId.set(42);
    TestBed.flushEffects();

    socketSubject.next({
      type: 'environment-deployment-invalidated',
      repositoryId: 42,
      environmentId: 7,
    });
    socketSubject.next({
      type: 'environment-deployment-invalidated',
      repositoryId: 42,
      environmentId: 8,
    });

    expect(queryClient.invalidateQueries).not.toHaveBeenCalled();
    vi.advanceTimersByTime(100);

    expect(queryClient.invalidateQueries).toHaveBeenCalledTimes(5);
  });
});
