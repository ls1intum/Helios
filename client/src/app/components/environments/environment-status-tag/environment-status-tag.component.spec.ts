import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { EnvironmentStatusDto } from '@app/core/modules/openapi';
import { TablerIconComponent } from 'angular-tabler-icons';
import { EnvironmentStatusTagComponent } from './environment-status-tag.component';

describe('EnvironmentStatusTagComponent', () => {
  let fixture: ComponentFixture<EnvironmentStatusTagComponent>;
  const now = new Date('2026-10-23T10:00:00Z');
  const status = (overrides: Partial<EnvironmentStatusDto> = {}): EnvironmentStatusDto => ({
    id: 1,
    success: true,
    httpStatusCode: 200,
    checkedAt: now.toISOString(),
    checkType: 'PUSH_UPDATE',
    state: 'RUNNING',
    ...overrides,
  });

  beforeEach(async () => {
    vi.useFakeTimers();
    vi.setSystemTime(now);
    await TestBed.configureTestingModule({ imports: [EnvironmentStatusTagComponent], providers: [provideZonelessChangeDetection()] }).compileComponents();
    fixture = TestBed.createComponent(EnvironmentStatusTagComponent);
  });

  afterEach(() => {
    fixture.destroy();
    vi.useRealTimers();
  });

  function render(value: EnvironmentStatusDto) {
    fixture.componentRef.setInput('status', value);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it.each([
    ['STARTING_UP', 'Starting up', 'info', 'loader'],
    ['DB_MIGRATION_STARTED', 'Migrating DB', 'info', 'database-cog'],
    ['DB_MIGRATION_FAILED', 'Migration failed', 'danger', 'database-x'],
    ['DB_MIGRATION_FINISHED', 'Migration finished', 'success', 'database'],
    ['RUNNING', 'Running', 'success', 'activity'],
    ['DEGRADED', 'Degraded', 'warn', undefined],
    ['SHUTTING_DOWN', 'Shutting down', 'info', 'power'],
    ['STOPPED', 'Stopped', 'info', 'square'],
    ['FAILED', 'Failed', 'danger', 'x'],
  ] as const)('renders the %s lifecycle state with its label, severity, and icon', (state, label, severity, icon) => {
    const host = render(status({ state }));
    expect(host.textContent?.trim()).toBe(label);
    expect(host.querySelector(`.p-tag-${severity}`)).toBeTruthy();
    const renderedIcon = fixture.debugElement.query(By.directive(TablerIconComponent));
    expect(renderedIcon?.componentInstance.name()).toBe(icon);
    expect(!!host.querySelector('svg')).toBe(icon !== undefined);
    expect(!!host.querySelector('.animate-spin')).toBe(state === 'STARTING_UP');
  });

  it.each([true, false])('uses success=%s for servers without lifecycle state data', success => {
    const host = render(status({ state: undefined, success }));
    expect(host.textContent?.trim()).toBe('Unknown');
    expect(host.querySelector(success ? '.p-tag-success' : '.p-tag-danger')).toBeTruthy();
  });

  it.each([
    ['HTTP_STATUS', true, 'Running'],
    ['HTTP_STATUS', false, 'Down'],
    ['ARTEMIS_INFO', true, 'Running'],
    ['ARTEMIS_INFO', false, 'Down'],
  ] as const)('renders health-check success for %s with success=%s', (checkType, success, label) => {
    const host = render(status({ checkType, success, state: 'FAILED' }));
    expect(host.textContent?.trim()).toBe(label);
    expect(host.querySelector(success ? '.p-tag-success' : '.p-tag-danger')).toBeTruthy();
  });

  it('adds a stale-update warning after a push update becomes three minutes old', () => {
    const host = render(status({ checkedAt: new Date(now.getTime() - 175_000).toISOString() }));
    expect(host.querySelector('i-tabler[name="alert-triangle"]')).toBeNull();
    vi.advanceTimersByTime(5_000);
    fixture.detectChanges();
    expect(host.querySelector('i-tabler[name="alert-triangle"]')).toBeTruthy();
    expect(host.textContent).toContain('Running');
  });

  it('clears the stale warning when a fresh update arrives', () => {
    render(status({ checkedAt: new Date(now.getTime() - 180_000).toISOString() }));
    const host = render(status());
    expect(host.querySelector('i-tabler[name="alert-triangle"]')).toBeNull();
  });

  it('does not add a stale push warning to an old HTTP check', () => {
    const host = render(status({ checkType: 'HTTP_STATUS', checkedAt: '2020-01-01T00:00:00Z' }));
    expect(host.querySelector('i-tabler[name="alert-triangle"]')).toBeNull();
  });

  it('stops its periodic refresh when destroyed', () => {
    render(status());
    expect(vi.getTimerCount()).toBeGreaterThan(0);
    fixture.destroy();
    expect(vi.getTimerCount()).toBe(0);
  });
});
