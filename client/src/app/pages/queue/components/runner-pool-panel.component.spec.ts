import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { RunnerPoolPanelComponent } from './runner-pool-panel.component';
import type { RunnerPool } from '../queue.api';

describe('RunnerPoolPanelComponent', () => {
  let fixture: ComponentFixture<RunnerPoolPanelComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RunnerPoolPanelComponent],
      providers: [provideZonelessChangeDetection(), provideNoopAnimations()],
    }).compileComponents();
    fixture = TestBed.createComponent(RunnerPoolPanelComponent);
  });

  it('renders busy / idle / offline counts for each pool', async () => {
    const pools: RunnerPool[] = [{ labels: ['self-hosted', 'linux'], online: 3, busy: 2, idle: 1, offline: 1 }];
    fixture.componentRef.setInput('pools', pools);
    fixture.detectChanges();
    await fixture.whenStable();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('busy');
    expect(text).toContain('idle');
    expect(text).toContain('offline');
    expect(text).toMatch(/2/);
    expect(text).toMatch(/1/);
  });

  it('shows placeholder when no pools', async () => {
    fixture.componentRef.setInput('pools', []);
    fixture.detectChanges();
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('No runner pools');
  });
});
