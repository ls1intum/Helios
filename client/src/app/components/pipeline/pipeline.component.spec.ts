import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PipelineComponent } from './pipeline.component';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideQueryClient, QueryClient } from '@tanstack/angular-query-experimental';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import type { WorkflowRunDto } from '@app/core/modules/openapi';
import { PermissionService } from '@app/core/services/permission.service';

describe('PipelineComponent', () => {
  let component: PipelineComponent;
  let fixture: ComponentFixture<PipelineComponent>;
  const permissionServiceMock = {
    hasWritePermission: () => true,
  };

  beforeEach(async () => {
    permissionServiceMock.hasWritePermission = () => true;

    await TestBed.configureTestingModule({
      imports: [PipelineComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideQueryClient(new QueryClient()),
        provideNoopAnimations(),
        { provide: PermissionService, useValue: permissionServiceMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PipelineComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('renders workflow logs icon link for each workflow run', async () => {
    const workflowRun: WorkflowRunDto = {
      id: 42,
      workflowId: 5,
      name: 'CI',
      displayTitle: 'CI',
      status: 'COMPLETED',
      conclusion: 'SUCCESS',
      htmlUrl: 'https://github.com/org/repo/actions/runs/42',
      label: 'TEST',
      createdAt: '2026-01-01T10:00:00Z',
      updatedAt: '2026-01-01T10:01:00Z',
    };

    fixture.componentRef.setInput('selector', { repositoryId: 7, pullRequestId: 11 });

    Object.defineProperty(component, 'branchQuery', {
      configurable: true,
      value: {
        isPending: () => false,
        data: () => [],
      },
    });
    Object.defineProperty(component, 'pullRequestQuery', {
      configurable: true,
      value: {
        isPending: () => false,
        data: () => [workflowRun],
      },
    });
    Object.defineProperty(component, 'groupsQuery', {
      configurable: true,
      value: {
        data: () => [{ id: 1, name: 'CI Group', memberships: [{ workflowId: 5, orderIndex: 0 }] }],
      },
    });

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const logsLink = fixture.nativeElement.querySelector('a[aria-label="Open workflow logs"]') as HTMLAnchorElement | null;
    expect(logsLink).toBeTruthy();
    expect(logsLink?.getAttribute('href')).toContain('/repo/7/ci-cd/runs/42/logs');
  });

  it('surfaces runs whose workflow is in no group under an "Ungrouped" fallback', async () => {
    const groupedRun: WorkflowRunDto = {
      id: 1,
      workflowId: 5,
      name: 'CI',
      displayTitle: 'CI',
      status: 'COMPLETED',
      conclusion: 'SUCCESS',
      htmlUrl: 'https://github.com/org/repo/actions/runs/1',
      label: 'TEST',
      createdAt: '2026-01-01T10:00:00Z',
      updatedAt: '2026-01-01T10:01:00Z',
    };
    const ungroupedRun: WorkflowRunDto = {
      id: 2,
      workflowId: 999,
      name: 'Orphan Workflow',
      displayTitle: 'Orphan Workflow',
      status: 'COMPLETED',
      conclusion: 'SUCCESS',
      htmlUrl: 'https://github.com/org/repo/actions/runs/2',
      label: 'NONE',
      createdAt: '2026-01-01T10:00:00Z',
      updatedAt: '2026-01-01T10:01:00Z',
    };

    fixture.componentRef.setInput('selector', { repositoryId: 7, pullRequestId: 11 });
    Object.defineProperty(component, 'branchQuery', { configurable: true, value: { isPending: () => false, data: () => [] } });
    Object.defineProperty(component, 'pullRequestQuery', {
      configurable: true,
      value: { isPending: () => false, data: () => [groupedRun, ungroupedRun] },
    });
    Object.defineProperty(component, 'groupsQuery', {
      configurable: true,
      value: { data: () => [{ id: 1, name: 'CI Group', memberships: [{ workflowId: 5, orderIndex: 0 }] }] },
    });

    const groups = component.pipeline().groups;
    const ciGroup = groups.find(g => g.name === 'CI Group');
    const ungrouped = groups.find(g => g.name === 'Ungrouped');
    expect(ciGroup?.workflows.map(w => w.id)).toEqual([1]);
    expect(ungrouped).toBeTruthy();
    expect(ungrouped?.workflows.map(w => w.id)).toEqual([2]);
  });

  it('does not add an "Ungrouped" fallback when every run belongs to a group', async () => {
    const groupedRun: WorkflowRunDto = {
      id: 1,
      workflowId: 5,
      name: 'CI',
      displayTitle: 'CI',
      status: 'COMPLETED',
      conclusion: 'SUCCESS',
      htmlUrl: 'https://github.com/org/repo/actions/runs/1',
      label: 'TEST',
      createdAt: '2026-01-01T10:00:00Z',
      updatedAt: '2026-01-01T10:01:00Z',
    };

    fixture.componentRef.setInput('selector', { repositoryId: 7, pullRequestId: 11 });
    Object.defineProperty(component, 'branchQuery', { configurable: true, value: { isPending: () => false, data: () => [] } });
    Object.defineProperty(component, 'pullRequestQuery', {
      configurable: true,
      value: { isPending: () => false, data: () => [groupedRun] },
    });
    Object.defineProperty(component, 'groupsQuery', {
      configurable: true,
      value: { data: () => [{ id: 1, name: 'CI Group', memberships: [{ workflowId: 5, orderIndex: 0 }] }] },
    });

    const groups = component.pipeline().groups;
    expect(groups.find(g => g.name === 'Ungrouped')).toBeUndefined();
  });

  it('does not render workflow logs icon link without write permission', async () => {
    const workflowRun: WorkflowRunDto = {
      id: 42,
      workflowId: 5,
      name: 'CI',
      displayTitle: 'CI',
      status: 'COMPLETED',
      conclusion: 'SUCCESS',
      htmlUrl: 'https://github.com/org/repo/actions/runs/42',
      label: 'TEST',
      createdAt: '2026-01-01T10:00:00Z',
      updatedAt: '2026-01-01T10:01:00Z',
    };

    permissionServiceMock.hasWritePermission = () => false;
    fixture.componentRef.setInput('selector', { repositoryId: 7, pullRequestId: 11 });

    Object.defineProperty(component, 'branchQuery', {
      configurable: true,
      value: {
        isPending: () => false,
        data: () => [],
      },
    });
    Object.defineProperty(component, 'pullRequestQuery', {
      configurable: true,
      value: {
        isPending: () => false,
        data: () => [workflowRun],
      },
    });
    Object.defineProperty(component, 'groupsQuery', {
      configurable: true,
      value: {
        data: () => [{ id: 1, name: 'CI Group', memberships: [{ workflowId: 5, orderIndex: 0 }] }],
      },
    });

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const logsLink = fixture.nativeElement.querySelector('a[aria-label="Open workflow logs"]') as HTMLAnchorElement | null;
    expect(logsLink).toBeNull();
  });
});
