import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideQueryClient, QueryClient } from '@tanstack/angular-query-experimental';
import { provideNoopAnimations } from '@angular/platform-browser/animations';

import { WorkflowRunDetailsComponent } from './workflow-run-details.component';
import { PermissionService } from '@app/core/services/permission.service';
import type { WorkflowRunDto, WorkflowJobDto } from '@app/core/modules/openapi';

function createRun(overrides: Partial<WorkflowRunDto> = {}): WorkflowRunDto {
  return {
    id: 1,
    name: 'CI',
    displayTitle: 'CI',
    status: 'COMPLETED',
    workflowId: 10,
    htmlUrl: 'https://github.com/org/repo/actions/runs/1',
    label: 'TEST',
    createdAt: '2025-01-01T00:00:00Z',
    updatedAt: '2025-01-01T00:01:00Z',
    ...overrides,
  };
}

describe('WorkflowRunDetailsComponent', () => {
  let component: WorkflowRunDetailsComponent;
  let fixture: ComponentFixture<WorkflowRunDetailsComponent>;

  const permissionServiceMock = {
    hasWritePermission: () => true,
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WorkflowRunDetailsComponent],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideQueryClient(new QueryClient()),
        provideNoopAnimations(),
        { provide: PermissionService, useValue: permissionServiceMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(WorkflowRunDetailsComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('repositoryId', 1);
    fixture.componentRef.setInput('runId', 1);

    const run = createRun({ id: 1 });
    const jobs: WorkflowJobDto[] = [
      {
        id: 11,
        name: 'build',
        status: 'completed',
        conclusion: 'success',
      },
    ];

    Object.defineProperty(component, 'runQuery', {
      configurable: true,
      value: {
        data: () => run,
        isPending: () => false,
        isError: () => false,
      },
    });

    Object.defineProperty(component, 'workflowJobsQuery', {
      configurable: true,
      value: {
        data: () => ({ jobs }),
        isPending: () => false,
        isError: () => false,
      },
    });

    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('renders workflow job list section', () => {
    const hostElement: HTMLElement = fixture.nativeElement;
    const jobList = hostElement.querySelector('app-workflow-job-list');
    expect(jobList).toBeTruthy();
  });
});
