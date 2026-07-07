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
    // Queued is a distinct, non-spinning clock, muted (not the running yellow) — scheduled, not idle.
    expect(icon('QUEUED', null).name).toBe('clock');
    expect(icon('QUEUED', null).tooltip).toBe('Queued');
    expect(icon('QUEUED', null).class).toBe('text-muted-color');
    expect(icon('QUEUED', null).class).not.toContain('animate-spin');
    // Awaiting a maintainer's approval is a distinct, actionable state (status or conclusion form),
    // and always the warning colour (orange) — never the yellow in-progress bucket, whichever form.
    expect(icon('WAITING', null).name).toBe('player-pause');
    expect(icon('WAITING', null).tooltip).toBe('Waiting for approval');
    expect(icon('WAITING', null).class).toContain('orange');
    expect(icon('ACTION_REQUIRED', null).tooltip).toBe('Waiting for approval');
    expect(icon('ACTION_REQUIRED', null).class).toContain('orange');
    expect(icon('COMPLETED', 'ACTION_REQUIRED').tooltip).toBe('Waiting for approval');
    expect(icon('COMPLETED', 'ACTION_REQUIRED').class).toContain('orange');
    // Only a genuinely absent run reads as the muted "not running yet".
    for (const s of ['PENDING', 'REQUESTED']) {
      expect(icon(s, null).name).toBe('circle-dashed');
      expect(icon(s, null).tooltip).toBe('Not running yet');
      expect(icon(s, null).class).not.toContain('animate-spin');
    }
    expect(icon('COMPLETED', 'SKIPPED').name).toBe('circle-minus');
    expect(icon('COMPLETED', 'CANCELLED').name).toBe('ban');
    // A terminal-but-neutral node is handled explicitly, not left as "Unknown".
    expect(icon('COMPLETED', 'NEUTRAL').tooltip).toBe('No result');
  });

  it('shows the commit freshness anchor, flagging when the newest commit is not built yet', async () => {
    mockPipeline({
      categories: [{ name: 'Build', nodes: [{ key: 'build-native', label: 'Native', status: 'QUEUED', conclusion: null, htmlUrl: null }] }],
      head: { sha: 'abc1234', upToDate: false },
    });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('abc1234');
    expect(text).toContain('newest commit not built yet');
    // The queued node renders its clock, not a dead dashed circle.
    expect(renderedIconNames()).toContain('clock');
  });

  it('renders the previous-commit confidence footer when present', async () => {
    mockPipeline({
      categories: [{ name: 'Build', nodes: [{ key: 'build-native', label: 'Native', status: 'QUEUED', conclusion: null, htmlUrl: null }] }],
      head: { sha: 'def5678', upToDate: true },
      previous: { sha: 'aaa1111', conclusion: 'SUCCESS' },
    });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Last built commit');
    expect(text).toContain('aaa1111');
    // The outcome is spelled out in words (not colour alone).
    expect(text).toContain('Passed');
    // "up to date" head does not show the not-built-yet warning.
    expect(text).not.toContain('newest commit not built yet');
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
