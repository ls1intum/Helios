import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { Subject } from 'rxjs';
import { webSocket } from 'rxjs/webSocket';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { WorkflowRunWebSocketService } from './workflow-run-websocket.service';
import { WsServerMessage } from './workflow-run-websocket.types';

vi.mock('rxjs/webSocket', () => ({
  webSocket: vi.fn(),
}));

describe('WorkflowRunWebSocketService', () => {
  let socketSubjects: Array<Subject<WsServerMessage>>;

  beforeEach(() => {
    vi.useFakeTimers();
    socketSubjects = [];
    vi.mocked(webSocket).mockImplementation(() => {
      const socketSubject = new Subject<WsServerMessage>();
      vi.spyOn(socketSubject, 'next');
      vi.spyOn(socketSubject, 'complete');
      socketSubjects.push(socketSubject);
      return socketSubject as never;
    });

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        WorkflowRunWebSocketService,
        {
          provide: KeycloakService,
          useValue: {
            keycloak: {
              token: 'access-token',
            },
          },
        },
      ],
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  it('opens the repository-scoped WebSocket with token and repository subprotocols', () => {
    const subscription = TestBed.inject(WorkflowRunWebSocketService).subscribe(7, 42).subscribe();

    expect(webSocket).toHaveBeenCalledOnce();
    const config = vi.mocked(webSocket).mock.calls[0][0];
    expect(config.url).toMatch(/\/ws\/workflow-runs$/);
    expect(config.protocol).toEqual(['helios.v1', 'helios.token.access-token', 'helios.repo.42']);
    expect(socketSubjects[0].next).toHaveBeenCalledWith({ type: 'subscribe', runId: 7 });

    subscription.unsubscribe();
  });

  it('reconnects and resubscribes after a clean WebSocket completion', () => {
    const subscription = TestBed.inject(WorkflowRunWebSocketService).subscribe(7, 42).subscribe();

    socketSubjects[0].complete();
    vi.advanceTimersByTime(999);
    expect(webSocket).toHaveBeenCalledTimes(1);

    vi.advanceTimersByTime(1);
    expect(webSocket).toHaveBeenCalledTimes(2);

    const reconnectConfig = vi.mocked(webSocket).mock.calls[1][0];
    reconnectConfig.openObserver?.next(undefined);
    expect(socketSubjects[1].next).toHaveBeenCalledWith({ type: 'subscribe', runId: 7 });

    subscription.unsubscribe();
  });

  it('reconnects after a WebSocket error', () => {
    const subscription = TestBed.inject(WorkflowRunWebSocketService).subscribe(7, 42).subscribe();

    socketSubjects[0].error(new Error('connection lost'));
    vi.advanceTimersByTime(1_000);

    expect(webSocket).toHaveBeenCalledTimes(2);

    subscription.unsubscribe();
  });

  it('does not reconnect after manual unsubscribe disconnects the socket', () => {
    const subscription = TestBed.inject(WorkflowRunWebSocketService).subscribe(7, 42).subscribe();

    subscription.unsubscribe();
    expect(socketSubjects[0].next).toHaveBeenCalledWith({ type: 'unsubscribe', runId: 7 });
    expect(socketSubjects[0].complete).toHaveBeenCalledOnce();

    vi.advanceTimersByTime(30_000);
    expect(webSocket).toHaveBeenCalledTimes(1);
  });
});
