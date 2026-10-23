import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EnvironmentStatusDto } from '@app/core/modules/openapi';
import { DateService } from '@app/core/services/date.service';
import { EnvironmentStatusInfoComponent } from './environment-status-info.component';

describe('EnvironmentStatusInfoComponent', () => {
  let fixture: ComponentFixture<EnvironmentStatusInfoComponent>;
  const formatDate = vi.fn();
  const now = new Date('2026-10-23T10:00:00Z');
  const status = (overrides: Partial<EnvironmentStatusDto> = {}): EnvironmentStatusDto => ({
    id: 1,
    success: true,
    httpStatusCode: 200,
    checkedAt: now.toISOString(),
    checkType: 'HTTP_STATUS',
    ...overrides,
  });

  beforeEach(async () => {
    vi.useFakeTimers();
    vi.setSystemTime(now);
    formatDate.mockReset().mockReturnValue('2026-10-23 09:30:00');
    await TestBed.configureTestingModule({
      imports: [EnvironmentStatusInfoComponent],
      providers: [provideZonelessChangeDetection(), { provide: DateService, useValue: { formatDate } }],
    }).compileComponents();
    fixture = TestBed.createComponent(EnvironmentStatusInfoComponent);
  });

  afterEach(() => {
    fixture.destroy();
    vi.useRealTimers();
  });

  const render = (value: EnvironmentStatusDto) => {
    fixture.componentRef.setInput('status', value);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  };

  it('renders HTTP status and refreshes the elapsed check time every second', () => {
    const host = render(status());
    expect(host.textContent).toContain('Latest status check');
    expect(host.textContent).toContain('Status Code:');
    expect(host.textContent).toContain('200');
    expect(host.textContent).toContain('0 seconds ago');
    vi.advanceTimersByTime(3_000);
    fixture.detectChanges();
    expect(host.textContent).toContain('3 seconds ago');
  });

  it('renders the push state and serializes additional metadata for display', () => {
    const host = render(
      status({
        checkType: 'PUSH_UPDATE',
        state: 'RUNNING',
        metadata: { version: '8.0', users: 42, healthy: true, cluster: { node: 'one' }, services: ['api', 'web'], empty: null },
      })
    );
    for (const text of ['Latest status update', 'RUNNING', 'Additional metadata', '8.0', '42', 'true', '{"node":"one"}', '["api","web"]', 'null']) {
      expect(host.textContent).toContain(text);
    }
    expect(host.textContent).not.toContain('Status Code:');
  });

  it.each([undefined, {}])('omits the metadata heading when no metadata is available: %s', metadata => {
    const host = render(status({ checkType: 'PUSH_UPDATE', metadata, state: undefined }));
    expect(host.textContent).toContain('N/A');
    expect(host.textContent).not.toContain('Additional metadata');
  });

  it('renders Artemis build details with a shortened commit and converts build seconds to milliseconds', () => {
    const host = render(
      status({
        checkType: 'ARTEMIS_INFO',
        metadata: { name: 'Artemis', group: 'de.tum', version: '8.0.0', buildTime: 123456, commitId: 'abcdef0123456789' },
      })
    );
    for (const text of ['Artemis Build', 'Artemis', 'de.tum', '8.0.0', '2026-10-23 09:30:00', 'abcdef0']) expect(host.textContent).toContain(text);
    expect(host.textContent).not.toContain('abcdef0123456789');
    expect(formatDate).toHaveBeenCalledWith(123456000, 'yyyy-MM-dd HH:mm:ss');
  });

  it('shows placeholders for missing Artemis build details and unavailable status codes', () => {
    const host = render(status({ checkType: 'ARTEMIS_INFO', metadata: {}, httpStatusCode: 0 }));
    expect(host.textContent).toContain('N/A');
    expect(host.textContent?.match(/-\/-/g)).toHaveLength(5);
    expect(formatDate).not.toHaveBeenCalled();
  });

  it('shows a placeholder when the build date cannot be formatted', () => {
    formatDate.mockReturnValue(null);
    const host = render(status({ checkType: 'ARTEMIS_INFO', metadata: { buildTime: 100 } }));
    expect(host.textContent?.match(/-\/-/g)).toHaveLength(5);
  });

  it('stops updating elapsed time after the component is destroyed', () => {
    render(status());
    fixture.destroy();
    expect(vi.getTimerCount()).toBe(0);
  });
});
