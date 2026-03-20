import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FlakyTestsOverviewComponent } from './flaky-tests-overview.component';
import { importProvidersFrom } from '@angular/core';
import { TestModule } from '@app/test.module';
import { SortMeta } from 'primeng/api';
import { TablePageEvent } from 'primeng/table';
import { expect, vi } from 'vitest';

import type { FlakyTestOverviewDto, GetFlakyTestsOverviewData } from '@app/core/modules/openapi';

const mockFlakyTests = [
  {
    testName: 'testDatabaseConnection',
    className: 'DatabaseServiceTest',
    testSuiteName: 'IntegrationTests',
    flakinessScore: 85,
    defaultBranchFailureRate: 0.03,
    combinedFailureRate: 0.05,
    lastUpdated: '2025-03-10T12:00:00Z',
  },
  {
    testName: 'testWebSocketReconnect',
    className: 'WebSocketClientTest',
    testSuiteName: 'IntegrationTests',
    flakinessScore: 45,
    defaultBranchFailureRate: 0.04,
    combinedFailureRate: 0.07,
    lastUpdated: '2025-03-10T11:00:00Z',
  },
  {
    testName: 'testConcurrentUserLogin',
    className: 'AuthServiceTest',
    testSuiteName: 'UnitTests',
    flakinessScore: 15,
    defaultBranchFailureRate: 0.01,
    combinedFailureRate: 0.02,
    lastUpdated: '2025-03-10T10:00:00Z',
  },
];

const mockOverview: FlakyTestOverviewDto = {
  summary: {
    totalTrackedTests: 10,
    flakyTestCount: 3,
    highFlakinessCount: 1,
    mediumFlakinessCount: 1,
    lowFlakinessCount: 1,
  },
  flakyTests: mockFlakyTests,
  filteredCount: 3,
};

function setMockQuery(component: FlakyTestsOverviewComponent, data: FlakyTestOverviewDto | undefined = mockOverview, options: { isPending?: boolean; isError?: boolean } = {}) {
  Object.defineProperty(component, 'query', {
    configurable: true,
    value: {
      data: () => data,
      isPending: () => options.isPending ?? false,
      isError: () => options.isError ?? false,
    },
  });
}

describe('FlakyTestsOverviewComponent', () => {
  let component: FlakyTestsOverviewComponent;
  let fixture: ComponentFixture<FlakyTestsOverviewComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FlakyTestsOverviewComponent],
      providers: [importProvidersFrom(TestModule)],
    }).compileComponents();

    fixture = TestBed.createComponent(FlakyTestsOverviewComponent);
    component = fixture.componentInstance;

    fixture.componentRef.setInput('repositoryId', 1);
    setMockQuery(component);

    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should expose flaky tests from query data', () => {
    expect(component.query.data()).toEqual(mockOverview);
    expect(component.flakyTests().length).toBe(3);
    expect(component.totalElements()).toBe(3);
  });

  describe('getSeverityTag', () => {
    it('should return High for score > 70', () => {
      expect(component.getSeverityTag(85)).toEqual({ label: 'High', severity: 'danger' });
    });

    it('should return Medium for score > 30 and <= 70', () => {
      expect(component.getSeverityTag(45)).toEqual({ label: 'Medium', severity: 'warn' });
    });

    it('should return Low for score <= 30', () => {
      expect(component.getSeverityTag(15)).toEqual({ label: 'Low', severity: 'info' });
    });
  });

  describe('formatScore', () => {
    it('should format score with one decimal', () => {
      expect(component.formatScore(85.123)).toBe('85.1');
    });
  });

  describe('formatRate', () => {
    it('should format rate as percentage', () => {
      expect(component.formatRate(0.05)).toBe('5.0%');
    });
  });

  describe('onPage', () => {
    it('calls paginationService.onPage with the event', () => {
      const onPageSpy = vi.spyOn(component.paginationService, 'onPage');
      const event = { first: 20, rows: 10 };

      component.onPage(event as TablePageEvent);

      expect(onPageSpy).toHaveBeenCalledWith(event);
    });
  });

  describe('onSort', () => {
    it('calls paginationService.onSort with the event', () => {
      const onSortSpy = vi.spyOn(component.paginationService, 'onSort');
      const event: SortMeta = { field: 'flakinessScore', order: 1 };

      component.onSort(event);

      expect(onSortSpy).toHaveBeenCalledWith(event);
    });
  });

  describe('clearFilters', () => {
    it('clears filter component search and pagination filters', () => {
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

  describe('queryOptions and pagination state', () => {
    function resetPaginationToDefaults() {
      const svc = component.paginationService;
      svc.page.set(1);
      svc.size.set(20);
      svc.sortField.set(undefined);
      svc.sortDirection.set('desc');
      svc.searchTerm.set('');
      svc.activeFilter.set(svc.filterOptions[0]);
    }

    function expectQueryKeyMatchesPaginationState() {
      const state = component.paginationService.paginationState();
      const filterType = state.filterType as NonNullable<GetFlakyTestsOverviewData['query']>['filterType'];
      expect(component.queryOptions().queryKey[0].query).toEqual({
        page: state.page,
        size: state.size,
        sortDirection: state.sortDirection,
        filterType,
        searchTerm: state.searchTerm,
      });
    }

    beforeEach(() => {
      resetPaginationToDefaults();
    });

    it('computed query key reflects page and size after onPage', () => {
      component.paginationService.onPage({ first: 20, rows: 10 } as TablePageEvent);

      expect(component.paginationService.page()).toBe(3);
      expect(component.paginationService.size()).toBe(10);
      expect(component.paginationService.paginationState()).toMatchObject({ page: 3, size: 10 });
      expectQueryKeyMatchesPaginationState();
    });

    it('computed query key reflects sort direction after onSort', () => {
      component.paginationService.onSort({ field: 'flakinessScore', order: 1 } as SortMeta);

      expect(component.paginationService.sortField()).toBe('flakinessScore');
      expect(component.paginationService.sortDirection()).toBe('asc');
      expectQueryKeyMatchesPaginationState();
    });

    it('computed query key reflects reset state after clearFilters', () => {
      component.paginationService.setSearchTerm('myTest');
      component.paginationService.setFilterType('HIGH');
      component.paginationService.onPage({ first: 30, rows: 10 } as TablePageEvent);

      const clearSearchSpy = vi.fn();
      (component as unknown as { filterComponent: { clearSearch: () => void } }).filterComponent = {
        clearSearch: clearSearchSpy,
      };

      component.clearFilters();

      expect(clearSearchSpy).toHaveBeenCalled();
      expect(component.paginationService.page()).toBe(1);
      expect(component.paginationService.searchTerm()).toBe('');
      expect(component.paginationService.activeFilter().value).toBe('ALL');
      expectQueryKeyMatchesPaginationState();
    });
  });
});
