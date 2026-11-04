import { TestBed } from '@angular/core/testing';
import { client } from '@app/core/modules/openapi/client.gen';
import { AppComponent } from './app.component';
import { provideZonelessChangeDetection } from '@angular/core';
import { MessageService } from 'primeng/api';
import { KeycloakService } from './core/services/keycloak/keycloak.service';
import { vi } from 'vitest';
import { HANDLED_LOCALLY_HEADER } from '@app/core/utils/error-handling';

describe('AppComponent', () => {
  afterEach(() => {
    client.interceptors.request.clear();
    client.interceptors.response.clear();
  });

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideZonelessChangeDetection(), MessageService, { provide: KeycloakService, useValue: { keycloak: { token: 'token' } } }],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(AppComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it('should not consume the original error response body in the response interceptor', async () => {
    TestBed.createComponent(AppComponent);

    const responseInterceptor = client.interceptors.response.fns.at(-1);
    const messageService = TestBed.inject(MessageService);
    vi.spyOn(messageService, 'add');

    const response = new Response(JSON.stringify({ message: 'Backend error' }), {
      status: 500,
      headers: { 'Content-Type': 'application/json' },
    });

    await responseInterceptor?.(response, new Request('http://localhost/api/deployments/cancel'), {} as never);

    await expect(response.text()).resolves.toBe('{"message":"Backend error"}');
  });

  it('shows the generic error toast for a failed request', async () => {
    TestBed.createComponent(AppComponent);
    const responseInterceptor = client.interceptors.response.fns.at(-1);
    const add = vi.spyOn(TestBed.inject(MessageService), 'add');

    const response = new Response(JSON.stringify({ message: 'Backend error' }), { status: 502, headers: { 'Content-Type': 'application/json' } });
    await responseInterceptor?.(response, new Request('http://localhost/api/deployments/1/approve'), {} as never);

    expect(add).toHaveBeenCalledWith(expect.objectContaining({ summary: 'Error 502', detail: 'Backend error' }));
  });

  it('stays quiet when the caller reports the failure itself', async () => {
    TestBed.createComponent(AppComponent);
    const responseInterceptor = client.interceptors.response.fns.at(-1);
    const add = vi.spyOn(TestBed.inject(MessageService), 'add');

    const response = new Response(JSON.stringify({ message: 'Backend error' }), { status: 409, headers: { 'Content-Type': 'application/json' } });
    const request = new Request('http://localhost/api/deployments/1/approve', { headers: { [HANDLED_LOCALLY_HEADER]: 'true' } });
    await responseInterceptor?.(response, request, {} as never);

    expect(add).not.toHaveBeenCalled();
  });
});
