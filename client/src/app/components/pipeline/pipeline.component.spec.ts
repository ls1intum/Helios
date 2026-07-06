import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PipelineComponent } from './pipeline.component';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideQueryClient, QueryClient } from '@tanstack/angular-query-experimental';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import type { PipelineDto } from '@app/core/modules/openapi';

describe('PipelineComponent', () => {
  let component: PipelineComponent;
  let fixture: ComponentFixture<PipelineComponent>;

  // Icon name is a property binding ([name]="..."), so read the rendered i-tabler inputs directly.
  // TablerIconComponent exposes `name` as a signal input, so unwrap it when it is callable.
  const renderedIconNames = () =>
    fixture.debugElement.queryAll(By.css('i-tabler')).map(de => {
      const n = de.componentInstance.name;
      return (typeof n === 'function' ? n() : n) as string;
    });

  const pipeline: PipelineDto = {
    categories: [
      {
        name: 'Build',
        nodes: [
          { key: 'build-native', label: 'Native', status: 'COMPLETED', conclusion: 'SUCCESS', htmlUrl: 'https://github.com/org/repo/actions/runs/1' },
          { key: 'build-docker', label: 'Docker', status: 'IN_PROGRESS', conclusion: null, htmlUrl: null },
        ],
      },
      {
        name: 'Tests',
        nodes: [
          { key: 'test-client', label: 'Client', status: 'COMPLETED', conclusion: 'FAILURE', htmlUrl: null },
          // No matching job yet → always visible, pending.
          { key: 'test-e2e', label: 'E2E', status: 'PENDING', conclusion: null, htmlUrl: null },
        ],
      },
    ],
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PipelineComponent],
      providers: [provideZonelessChangeDetection(), provideRouter([]), provideQueryClient(new QueryClient()), provideNoopAnimations()],
    }).compileComponents();

    fixture = TestBed.createComponent(PipelineComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  // Use a branch selector so branchName flips null -> 'main', invalidating the activeQuery computed
  // and forcing it to read the overridden branchPipelineQuery.
  const mockPipeline = (data: PipelineDto | undefined, pending = false) => {
    Object.defineProperty(component, 'branchPipelineQuery', { configurable: true, value: { isPending: () => pending, data: () => data } });
    Object.defineProperty(component, 'pullRequestPipelineQuery', { configurable: true, value: { isPending: () => false, data: () => undefined } });
    fixture.componentRef.setInput('selector', { repositoryId: 7, branchName: 'main' });
  };

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('renders every canonical node with a status icon, including pending nodes', async () => {
    mockPipeline(pipeline);

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const text = el.textContent ?? '';
    // Category headers + node labels are all present.
    expect(text).toContain('Build');
    expect(text).toContain('Tests');
    expect(text).toContain('Native');
    expect(text).toContain('Docker');
    expect(text).toContain('E2E');

    // Success / failure / in-progress / not-started each render their distinct icon.
    expect(renderedIconNames()).toEqual(expect.arrayContaining(['circle-check', 'circle-x', 'progress', 'circle-dashed']));
  });

  it('exposes the configured categories via the pipeline signal', () => {
    mockPipeline(pipeline);

    expect(component.categories().map(c => c.name)).toEqual(['Build', 'Tests']);
    expect(component.hasCategories()).toBe(true);
  });

  it('nodeIcon maps every state to a distinct icon (queued != running, fail-fast != spinner)', () => {
    const icon = (status: string | null, conclusion: string | null) => component.nodeIcon({ status, conclusion });
    expect(icon('COMPLETED', 'SUCCESS').name).toBe('circle-check');
    // The whole failure family renders red, not just FAILURE.
    for (const c of ['FAILURE', 'TIMED_OUT', 'STARTUP_FAILURE']) {
      expect(icon('COMPLETED', c).name).toBe('circle-x');
    }
    // Fail-fast: still running, a leg already failed → red X, and NOT spinning.
    expect(icon('IN_PROGRESS', 'FAILURE').name).toBe('circle-x');
    expect(icon('IN_PROGRESS', 'FAILURE').class).not.toContain('animate-spin');
    // Running is the only spinner.
    expect(icon('IN_PROGRESS', null).name).toBe('progress');
    expect(icon('IN_PROGRESS', null).class).toContain('animate-spin');
    // Queued/waiting/pending are "not running yet" (dashed), never a spinner.
    for (const s of ['QUEUED', 'WAITING', 'REQUESTED', 'PENDING']) {
      expect(icon(s, null).name).toBe('circle-dashed');
      expect(icon(s, null).class).not.toContain('animate-spin');
    }
    expect(icon('COMPLETED', 'SKIPPED').name).toBe('circle-minus');
    expect(icon('COMPLETED', 'CANCELLED').name).toBe('ban');
    // A terminal-but-neutral node is handled explicitly, not left as "Unknown".
    expect(icon('COMPLETED', 'NEUTRAL').tooltip).toBe('Neutral');
  });

  it('renders the merge-gate badge when a gate is present', async () => {
    const p: PipelineDto = {
      categories: [{ name: 'Build', nodes: [{ key: 'build-native', label: 'Native', status: 'PENDING', conclusion: null, htmlUrl: null }] }],
      gate: { key: 'ci-gate', label: 'All required CI passed', status: 'COMPLETED', conclusion: 'SUCCESS', htmlUrl: null },
    };
    mockPipeline(p);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.textContent ?? '').toContain('All required CI passed');
    expect(renderedIconNames()).toContain('circle-check');
  });

  it('shows the empty state when no categories are configured', async () => {
    mockPipeline({ categories: [] });

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(component.hasCategories()).toBe(false);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('No pipeline configured.');
  });
});
