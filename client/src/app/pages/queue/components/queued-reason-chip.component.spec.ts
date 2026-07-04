import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { QueuedReasonChipComponent } from './queued-reason-chip.component';

describe('QueuedReasonChipComponent', () => {
  let fixture: ComponentFixture<QueuedReasonChipComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [QueuedReasonChipComponent],
      providers: [provideZonelessChangeDetection(), provideNoopAnimations()],
    }).compileComponents();
    fixture = TestBed.createComponent(QueuedReasonChipComponent);
  });

  function render(reason: string | null) {
    fixture.componentRef.setInput('reason', reason);
    fixture.detectChanges();
  }

  it('shows em-dash when reason is null', () => {
    render(null);
    expect((fixture.nativeElement as HTMLElement).textContent?.trim()).toBe('—');
  });

  it('maps PENDING_APPROVAL to a human label', () => {
    render('PENDING_APPROVAL');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('pending approval');
  });

  it('maps NO_RUNNER_ONLINE to a human label with danger severity', () => {
    render('NO_RUNNER_ONLINE');
    expect(fixture.componentInstance.severity()).toBe('danger');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('no runner online');
  });

  it('maps RUNNERS_BUSY with warn severity', () => {
    render('RUNNERS_BUSY');
    expect(fixture.componentInstance.severity()).toBe('warn');
  });

  it('falls back to the raw reason value when unrecognised', () => {
    render('SOMETHING_ELSE');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('SOMETHING_ELSE');
  });
});
