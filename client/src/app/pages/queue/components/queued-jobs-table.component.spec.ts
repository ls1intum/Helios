import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { QueuedJobsTableComponent } from './queued-jobs-table.component';
import type { QueuedJob } from '../queue.api';

describe('QueuedJobsTableComponent', () => {
  let fixture: ComponentFixture<QueuedJobsTableComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [QueuedJobsTableComponent],
      providers: [provideZonelessChangeDetection(), provideNoopAnimations()],
    }).compileComponents();
    fixture = TestBed.createComponent(QueuedJobsTableComponent);
  });

  it('renders an empty-state message with no jobs', async () => {
    fixture.componentRef.setInput('jobs', []);
    fixture.detectChanges();
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('No queued jobs');
  });

  it('renders rows for queued jobs and respects formatSeconds for nullable values', async () => {
    const jobs: QueuedJob[] = [
      {
        jobId: 1,
        runId: 99,
        workflowName: 'CI',
        jobName: 'build',
        headBranch: 'main',
        labels: ['self-hosted', 'linux'],
        waitSeconds: 65,
        etaSeconds: null,
        positionInQueue: 1,
        queuedReason: 'NO_RUNNER_ONLINE',
        isStuck: true,
        runnerKind: 'SELF_HOSTED',
      },
    ];
    fixture.componentRef.setInput('jobs', jobs);
    fixture.detectChanges();
    await fixture.whenStable();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('CI');
    expect(text).toContain('build');
    // wait 65s → '1m' rounded
    expect(text).toContain('1m');
    // null eta → em-dash
    expect(text).toContain('—');
  });
});
