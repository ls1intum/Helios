import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { QueueDepthPanelComponent } from './queue-depth-panel.component';
import type { LabelSetDepth } from '../queue.api';

describe('QueueDepthPanelComponent', () => {
  let fixture: ComponentFixture<QueueDepthPanelComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [QueueDepthPanelComponent],
      providers: [provideZonelessChangeDetection(), provideNoopAnimations()],
    }).compileComponents();
    fixture = TestBed.createComponent(QueueDepthPanelComponent);
  });

  it('renders a card per label-set', async () => {
    const labelSets: LabelSetDepth[] = [
      {
        labels: ['self-hosted', 'linux'],
        queued: 3,
        inProgress: 1,
        oldestQueuedSeconds: 120,
        runnerKind: 'SELF_HOSTED',
      },
      {
        labels: ['ubuntu-latest'],
        queued: 0,
        inProgress: 2,
        oldestQueuedSeconds: null,
        runnerKind: 'GITHUB_HOSTED',
      },
    ];
    fixture.componentRef.setInput('labelSets', labelSets);
    fixture.detectChanges();
    await fixture.whenStable();

    const host: HTMLElement = fixture.nativeElement;
    const cards = host.querySelectorAll('p-card');
    expect(cards.length).toBe(2);
  });

  it('shows empty placeholder when no label sets', async () => {
    fixture.componentRef.setInput('labelSets', []);
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.nativeElement.textContent).toContain('No active jobs');
  });

  it('formats seconds into compact units', () => {
    const c = fixture.componentInstance;
    expect(c.formatSeconds(null)).toBe('—');
    expect(c.formatSeconds(45)).toBe('45s');
    expect(c.formatSeconds(75)).toBe('1m');
    expect(c.formatSeconds(7200)).toBe('2.0h');
  });
});
