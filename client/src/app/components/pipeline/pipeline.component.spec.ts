import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PipelineComponent } from './pipeline.component';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideQueryClient, QueryClient } from '@tanstack/angular-query-experimental';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import type { PipelineDto } from '@app/core/modules/openapi';

describe('PipelineComponent', () => {
  let component: PipelineComponent;
  let fixture: ComponentFixture<PipelineComponent>;

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

    // Success / failure / in-progress / pending each render their distinct icon.
    expect(el.querySelector('i-tabler[name="circle-check"]')).toBeTruthy();
    expect(el.querySelector('i-tabler[name="circle-x"]')).toBeTruthy();
    expect(el.querySelector('i-tabler[name="progress"]')).toBeTruthy();
    // The always-visible-but-not-started node uses the dashed (non-spinning) icon.
    expect(el.querySelector('i-tabler[name="circle-dashed"]')).toBeTruthy();
  });

  it('exposes the configured categories via the pipeline signal', () => {
    mockPipeline(pipeline);

    expect(component.categories().map(c => c.name)).toEqual(['Build', 'Tests']);
    expect(component.hasCategories()).toBe(true);
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
