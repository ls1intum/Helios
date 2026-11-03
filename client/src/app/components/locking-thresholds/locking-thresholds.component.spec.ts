import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { GitRepoSettingsDto } from '@app/core/modules/openapi';
import { client } from '@app/core/modules/openapi/client.gen';
import {
  getAllEnabledEnvironmentsQueryKey,
  getAllEnvironmentsQueryKey,
  getEnvironmentsByUserLockingQueryKey,
  getGitRepoSettingsQueryKey,
} from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { provideQueryClient, QueryClient } from '@tanstack/angular-query-experimental';
import { MessageService } from 'primeng/api';
import { LockingThresholdsComponent } from './locking-thresholds.component';

describe('LockingThresholdsComponent', () => {
  let fixture: ComponentFixture<LockingThresholdsComponent>;
  let queryClient: QueryClient;
  const originalConfig = client.getConfig();
  const fetch = vi.fn();
  const addMessage = vi.fn();
  const settingsKey = () => getGitRepoSettingsQueryKey({ path: { repositoryId: 7 } });
  const response = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

  beforeEach(async () => {
    vi.clearAllMocks();
    fetch.mockImplementation(async () => response({}));
    client.setConfig({ baseUrl: 'http://localhost', fetch });
    queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity }, mutations: { retry: false } } });
    await TestBed.configureTestingModule({
      imports: [LockingThresholdsComponent],
      providers: [provideZonelessChangeDetection(), provideNoopAnimations(), provideQueryClient(queryClient), { provide: MessageService, useValue: { add: addMessage } }],
    }).compileComponents();
  });

  afterEach(() => {
    fixture?.destroy();
    queryClient.clear();
    client.setConfig({ baseUrl: originalConfig.baseUrl, fetch: originalConfig.fetch });
  });

  const render = async (settings: GitRepoSettingsDto = { lockExpirationThreshold: 90, lockReservationThreshold: 20 }) => {
    queryClient.setQueryData(settingsKey(), settings);
    fixture = TestBed.createComponent(LockingThresholdsComponent);
    fixture.componentRef.setInput('repositoryId', 7);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    await fixture.whenStable();
  };
  const host = () => fixture.nativeElement as HTMLElement;
  const numberInput = (index: number) => host().querySelectorAll<HTMLInputElement>('p-inputnumber input')[index];
  const toggle = (index: number) => host().querySelectorAll<HTMLInputElement>('p-toggleswitch input')[index];
  const save = () => Array.from(host().querySelectorAll('button')).find(button => button.textContent?.includes('Update Thresholds'))!;
  const edit = async (index: number, value: string) => {
    const input = numberInput(index);
    input.value = value;
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('blur'));
    await fixture.whenStable();
  };

  it('renders saved thresholds and disables saving until a setting changes', async () => {
    await render();
    expect(numberInput(0).value).toBe('90 minutes');
    expect(numberInput(1).value).toBe('20 minutes');
    expect(toggle(0).checked).toBe(true);
    expect(toggle(1).checked).toBe(true);
    expect(save().disabled).toBe(true);
    expect(fetch).not.toHaveBeenCalled();
  });

  it.each([{ lockExpirationThreshold: -1, lockReservationThreshold: -1 }, {}])('renders disabled thresholds as empty disabled fields: %j', async settings => {
    await render(settings);
    expect(toggle(0).checked).toBe(false);
    expect(toggle(1).checked).toBe(false);
    expect(numberInput(0).disabled).toBe(true);
    expect(numberInput(1).disabled).toBe(true);
    expect(numberInput(0).value).toBe('');
    expect(numberInput(1).value).toBe('');
    expect(save().disabled).toBe(true);
  });

  it('enables expiration and reservation with their defaults and clears fields when switched off', async () => {
    await render({ lockExpirationThreshold: -1, lockReservationThreshold: -1 });
    toggle(0).click();
    toggle(1).click();
    await fixture.whenStable();
    expect(numberInput(0).value).toBe('60 minutes');
    expect(numberInput(1).value).toBe('30 minutes');
    expect(numberInput(0).disabled).toBe(false);
    expect(numberInput(1).disabled).toBe(false);
    expect(save().disabled).toBe(false);
    toggle(0).click();
    toggle(1).click();
    await fixture.whenStable();
    expect(numberInput(0).value).toBe('');
    expect(numberInput(1).value).toBe('');
    expect(save().disabled).toBe(true);
  });

  it('tracks numeric edits and returns to an unchanged state when the original value is restored', async () => {
    await render();
    await edit(0, '125');
    expect(save().disabled).toBe(false);
    await edit(0, '90');
    expect(save().disabled).toBe(true);
    await edit(1, '45');
    expect(save().disabled).toBe(false);
  });

  it('allows clearing a threshold field without retaining its previous displayed value', async () => {
    await render();
    await edit(0, '');
    await edit(1, '');
    expect(numberInput(0).value).toBe('');
    expect(numberInput(1).value).toBe('');
  });

  it('saves enabled defaults through the API and refreshes lock and environment consumers', async () => {
    await render({ lockExpirationThreshold: -1, lockReservationThreshold: -1 });
    const keys = [getAllEnvironmentsQueryKey(), getAllEnabledEnvironmentsQueryKey(), getEnvironmentsByUserLockingQueryKey()];
    keys.forEach(key => queryClient.setQueryData(key, []));
    toggle(0).click();
    toggle(1).click();
    await fixture.whenStable();
    save().click();
    await vi.waitFor(() => expect(addMessage).toHaveBeenCalled());
    const request = fetch.mock.calls[0][0] as Request;
    expect(request.method).toBe('PUT');
    expect(request.url).toBe('http://localhost/api/settings/7/settings');
    expect(await request.json()).toEqual({ lockExpirationThreshold: 60, lockReservationThreshold: 30 });
    keys.forEach(key => expect(queryClient.getQueryState(key)?.isInvalidated).toBe(true));
    expect(addMessage).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success', summary: 'Success' }));
  });

  it('sends the disabled sentinel for both thresholds when turning existing limits off', async () => {
    await render();
    toggle(0).click();
    toggle(1).click();
    await fixture.whenStable();
    save().click();
    await vi.waitFor(() => expect(fetch).toHaveBeenCalled());
    expect(await (fetch.mock.calls[0][0] as Request).json()).toEqual({ lockExpirationThreshold: -1, lockReservationThreshold: -1 });
  });

  it('does not announce success or invalidate consumers after a failed save', async () => {
    await render();
    fetch.mockImplementation(async () => response({ message: 'Save failed' }, 500));
    const key = getAllEnvironmentsQueryKey();
    queryClient.setQueryData(key, []);
    await edit(0, '125');
    save().click();
    await vi.waitFor(() => expect(fixture.componentInstance.mutateGitRepoSettings.isError()).toBe(true));
    expect(addMessage).not.toHaveBeenCalled();
    expect(queryClient.getQueryState(key)?.isInvalidated).toBe(false);
    expect(save().disabled).toBe(false);
  });
});
