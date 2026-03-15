import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideQueryClient, QueryClient } from '@tanstack/angular-query-experimental';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { vi } from 'vitest';

import { WorkflowRunsTableComponent, createWorkflowRunsFilterOptions } from './workflow-runs-table.component';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import type { WorkflowRunDto } from '@app/core/modules/openapi';

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
    updatedAt: '2025-01-01T00:00:00Z',
    ...overrides,
  };
}

type QueryData = {
  runs?: WorkflowRunDto[];
  totalElements?: number;
};

function setMockQuery(
  component: WorkflowRunsTableComponent,
  options: {
    data?: QueryData | undefined;
    isPending?: boolean;
    isError?: boolean;
    refetch?: () => void;
  } = {}
) {
  const refetch = options.refetch ?? vi.fn();

  Object.defineProperty(component, 'query', {
    configurable: true,
    value: {
      data: () => options.data,
      isPending: () => options.isPending ?? false,
      isError: () => options.isError ?? false,
      refetch,
    },
  });

  return { refetch };
}

describe('WorkflowRunsTableComponent', () => {
  let component: WorkflowRunsTableComponent;
  let fixture: ComponentFixture<WorkflowRunsTableComponent>;

  const keycloakMock = {
    isLoggedIn: () => false,
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WorkflowRunsTableComponent],
      providers: [provideZonelessChangeDetection(), provideQueryClient(new QueryClient()), provideNoopAnimations(), { provide: KeycloakService, useValue: keycloakMock }],
    }).compileComponents();

    fixture = TestBed.createComponent(WorkflowRunsTableComponent);
    component = fixture.componentInstance;

    setMockQuery(component, { data: undefined, isPending: false, isError: false });

    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('runs() and totalElements()', () => {
    it('should return empty array and 0 when query has no data', () => {
      expect(component.runs()).toEqual([]);
      expect(component.totalElements()).toBe(0);
    });

    it('returns runs and totalElements from query data', () => {
      const runs = [createRun({ id: 1 }), createRun({ id: 2, name: 'Build' })];
      setMockQuery(component, {
        data: {
          runs,
          totalElements: 42,
        },
      });

      expect(component.runs()).toEqual(runs);
      expect(component.totalElements()).toBe(42);
    });
  });

  describe('getWorkflowStatusIcon', () => {
    type IconName = ReturnType<WorkflowRunsTableComponent['getWorkflowStatusIcon']>;

    const cases: Array<[Partial<WorkflowRunDto>, IconName]> = [
      [{ conclusion: 'SUCCESS' }, 'circle-check'],
      [{ conclusion: 'FAILURE' }, 'circle-x'],
      [{ conclusion: 'STARTUP_FAILURE' }, 'circle-x'],
      [{ conclusion: 'TIMED_OUT' }, 'circle-x'],
      [{ conclusion: 'CANCELLED' }, 'circle-x'],
      [{ status: 'IN_PROGRESS' }, 'progress'],
      [{ status: 'QUEUED' }, 'clock-hour-4'],
      [{ status: 'WAITING' }, 'clock-hour-4'],
      [{ status: 'PENDING' }, 'clock-hour-4'],
      [{ status: 'REQUESTED' }, 'clock-hour-4'],
      [{ status: 'ACTION_REQUIRED' }, 'alert-triangle'],
      [{ conclusion: 'ACTION_REQUIRED' }, 'alert-triangle'],
      [{ status: 'COMPLETED' }, 'circle-x'],
    ];

    cases.forEach(([overrides, expected]: [Partial<WorkflowRunDto>, IconName]) => {
      it(`returns ${expected} for ${JSON.stringify(overrides)}`, () => {
        const actual = component.getWorkflowStatusIcon(createRun(overrides));
        expect(actual).toBe(expected);
      });
    });
  });

  describe('getWorkflowStatusClass', () => {
    type WorkflowStatusClass = ReturnType<WorkflowRunsTableComponent['getWorkflowStatusClass']>;

    const cases: Array<[Partial<WorkflowRunDto>, WorkflowStatusClass]> = [
      [{ conclusion: 'SUCCESS' }, 'text-green-500'],
      [{ conclusion: 'FAILURE' }, 'text-red-500'],
      [{ conclusion: 'STARTUP_FAILURE' }, 'text-red-500'],
      [{ conclusion: 'TIMED_OUT' }, 'text-red-500'],
      [{ conclusion: 'CANCELLED' }, 'text-surface-500'],
      [{ status: 'QUEUED' }, 'text-amber-500'],
      [{ status: 'WAITING' }, 'text-amber-500'],
      [{ status: 'PENDING' }, 'text-amber-500'],
      [{ status: 'REQUESTED' }, 'text-amber-500'],
      [{ status: 'ACTION_REQUIRED' }, 'text-orange-500'],
      [{ conclusion: 'ACTION_REQUIRED' }, 'text-orange-500'],
      [{ status: 'COMPLETED' }, 'text-surface-500'],
    ];

    cases.forEach(([overrides, expected]: [Partial<WorkflowRunDto>, WorkflowStatusClass]) => {
      it(`returns ${expected} for ${JSON.stringify(overrides)}`, () => {
        const actual: WorkflowStatusClass = component.getWorkflowStatusClass(createRun(overrides));
        expect(actual).toBe(expected);
      });
    });

    it('returns animated blue class for IN_PROGRESS', () => {
      const actual: WorkflowStatusClass = component.getWorkflowStatusClass(createRun({ status: 'IN_PROGRESS' }));

      expect(actual).toContain('text-blue-500');
      expect(actual).toContain('animate-spin');
    });
  });

  describe('getTestStatusIcon', () => {
    type TestIcon = ReturnType<WorkflowRunsTableComponent['getTestStatusIcon']>;

    const cases: Array<[Partial<WorkflowRunDto>, TestIcon]> = [
      [{ testProcessingStatus: 'PROCESSED' }, 'circle-check'],
      [{ testProcessingStatus: 'FAILED' }, 'alert-triangle'],
      [{ testProcessingStatus: 'PROCESSING' }, 'progress'],
      [{}, 'circle-check'],
    ];

    cases.forEach(([overrides, expected]: [Partial<WorkflowRunDto>, TestIcon]) => {
      it(`returns ${expected} for ${JSON.stringify(overrides)}`, () => {
        const actual: TestIcon = component.getTestStatusIcon(createRun(overrides));
        expect(actual).toBe(expected);
      });
    });
  });

  describe('getTestStatusClass', () => {
    type TestStatusClass = ReturnType<WorkflowRunsTableComponent['getTestStatusClass']>;

    const cases: Array<[Partial<WorkflowRunDto>, TestStatusClass]> = [
      [{ testProcessingStatus: 'PROCESSED' }, 'text-green-500'],
      [{ testProcessingStatus: 'FAILED' }, 'text-red-500'],
      [{}, 'text-surface-500'],
    ];

    cases.forEach(([overrides, expected]: [Partial<WorkflowRunDto>, TestStatusClass]) => {
      it(`returns ${expected} for ${JSON.stringify(overrides)}`, () => {
        const actual: TestStatusClass = component.getTestStatusClass(createRun(overrides));
        expect(actual).toBe(expected);
      });
    });

    it('returns animated blue class for PROCESSING', () => {
      const actual: TestStatusClass = component.getTestStatusClass(createRun({ testProcessingStatus: 'PROCESSING' }));

      expect(actual).toContain('text-blue-500');
      expect(actual).toContain('animate-spin');
    });
  });

  describe('statusSeverity', () => {
    type StatusSeverity = ReturnType<WorkflowRunsTableComponent['statusSeverity']>;

    const cases: Array<[Partial<WorkflowRunDto>, StatusSeverity]> = [
      [{ conclusion: 'SUCCESS' }, 'success'],
      [{ conclusion: 'FAILURE' }, 'danger'],
      [{ conclusion: 'STARTUP_FAILURE' }, 'danger'],
      [{ conclusion: 'TIMED_OUT' }, 'danger'],
      [{ conclusion: 'CANCELLED' }, 'secondary'],
      [{ status: 'IN_PROGRESS' }, 'info'],
      [{ status: 'QUEUED' }, 'warn'],
      [{ status: 'WAITING' }, 'warn'],
      [{ status: 'PENDING' }, 'warn'],
      [{ status: 'REQUESTED' }, 'warn'],
      [{ status: 'COMPLETED' }, 'secondary'],
    ];

    cases.forEach(([overrides, expected]: [Partial<WorkflowRunDto>, StatusSeverity]) => {
      it(`returns ${expected} for ${JSON.stringify(overrides)}`, () => {
        const actual: StatusSeverity = component.statusSeverity(createRun(overrides));
        expect(actual).toBe(expected);
      });
    });
  });

  describe('openRunExternal', () => {
    it('opens run htmlUrl in new tab and stops propagation', () => {
      const run = createRun({ htmlUrl: 'https://github.com/org/repo/actions/runs/42' });
      const ev = new Event('click') as Event & { stopPropagation: () => void };
      ev.stopPropagation = vi.fn();
      const openSpy = vi.spyOn(window, 'open').mockImplementation(() => null);

      component.openRunExternal(ev, run);

      expect(openSpy).toHaveBeenCalledWith('https://github.com/org/repo/actions/runs/42', '_blank');
      expect(ev.stopPropagation).toHaveBeenCalled();
    });
  });

  describe('onPage', () => {
    it('calls pagination service onPage with the event', () => {
      const onPageSpy = vi.spyOn(component.paginationService, 'onPage');

      const event = { first: 20, rows: 20, page: 1 };

      component.onPage(event as Parameters<WorkflowRunsTableComponent['onPage']>[0]);

      expect(onPageSpy).toHaveBeenCalledWith(event);
    });
  });

  describe('onSort', () => {
    it('updates pagination sort state', () => {
      const onSortSpy = vi.spyOn(component.paginationService, 'onSort');
      const event = { field: 'name', order: 1 };

      component.onSort(event as Parameters<WorkflowRunsTableComponent['onSort']>[0]);

      expect(onSortSpy).toHaveBeenCalledWith(event);
    });
  });

  describe('clearFilters', () => {
    it('clears filter component and pagination filters', () => {
      const clearSearchSpy = vi.fn();
      const clearFiltersSpy = vi.spyOn(component.paginationService, 'clearFilters');
      (component as unknown as { filterComponent: { clearSearch: () => void } }).filterComponent = {
        clearSearch: clearSearchSpy,
      };

      component.clearFilters();

      expect(clearSearchSpy).toHaveBeenCalled();
      expect(clearFiltersSpy).toHaveBeenCalled();
    });
  });

  describe('createWorkflowRunsFilterOptions', () => {
    it('exports filter options with expected labels and values', () => {
      const options = createWorkflowRunsFilterOptions();

      expect(options).toEqual([
        { name: 'All runs', value: 'ALL' },
        { name: 'Not started', value: 'NOT_STARTED' },
        { name: 'In progress', value: 'IN_PROGRESS' },
        { name: 'Succeeded', value: 'SUCCESS' },
        { name: 'Failed', value: 'FAILURE' },
        { name: 'Cancelled', value: 'CANCELLED' },
        { name: 'Action required', value: 'ACTION_REQUIRED' },
      ]);
    });
  });
});
