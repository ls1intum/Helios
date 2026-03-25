import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FlakyTestsOverviewComponent } from './flaky-tests-overview.component';
import { importProvidersFrom, signal } from '@angular/core';
import { TestModule } from '@app/test.module';

const mockFlakyTests = [
  {
    testName: 'testDatabaseConnection',
    className: 'DatabaseServiceTest',
    testSuiteName: 'IntegrationTests',
    flakinessScore: 85,
    defaultBranchFailureRate: 0.03,
    combinedFailureRate: 0.05,
    totalRuns: 320,
    failedRuns: 12,
    lastUpdated: '2025-03-10T12:00:00Z',
  },
  {
    testName: 'testWebSocketReconnect',
    className: 'WebSocketClientTest',
    testSuiteName: 'IntegrationTests',
    flakinessScore: 45,
    defaultBranchFailureRate: 0.04,
    combinedFailureRate: 0.07,
    totalRuns: 280,
    failedRuns: 16,
    lastUpdated: '2025-03-10T11:00:00Z',
  },
  {
    testName: 'testConcurrentUserLogin',
    className: 'AuthServiceTest',
    testSuiteName: 'UnitTests',
    flakinessScore: 15,
    defaultBranchFailureRate: 0.01,
    combinedFailureRate: 0.02,
    totalRuns: 500,
    failedRuns: 5,
    lastUpdated: '2025-03-10T10:00:00Z',
  },
];

const mockOverview = {
  summary: {
    totalTrackedTests: 10,
    flakyTestCount: 3,
    averageFlakinessScore: 48.3,
    highFlakinessCount: 1,
    mediumFlakinessCount: 1,
    lowFlakinessCount: 1,
  },
  flakyTests: mockFlakyTests,
};

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

    // Mock query for testing
    component.flakyTestsQuery = {
      ...component.flakyTestsQuery,
      data: signal(mockOverview),
      isPending: signal(false),
      isError: signal(false),
    } as unknown as typeof component.flakyTestsQuery;

    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should display summary from query data', () => {
    expect(component.flakyTestsQuery.data()).toEqual(mockOverview);
    expect(component.filteredFlakyTests().length).toBe(3);
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

  describe('filtering', () => {
    it('should filter by search term (test name)', () => {
      component.debouncedSearch.set('database');
      fixture.detectChanges();
      expect(component.filteredFlakyTests().length).toBe(1);
      expect(component.filteredFlakyTests()[0].testName).toBe('testDatabaseConnection');
    });

    it('should filter by search term (class name)', () => {
      component.debouncedSearch.set('WebSocket');
      fixture.detectChanges();
      expect(component.filteredFlakyTests().length).toBe(1);
      expect(component.filteredFlakyTests()[0].className).toBe('WebSocketClientTest');
    });

    it('should filter by severity high', () => {
      component.setSeverityFilter('high');
      fixture.detectChanges();
      expect(component.filteredFlakyTests().length).toBe(1);
      expect(component.filteredFlakyTests()[0].flakinessScore).toBe(85);
    });

    it('should filter by severity medium', () => {
      component.setSeverityFilter('medium');
      fixture.detectChanges();
      expect(component.filteredFlakyTests().length).toBe(1);
      expect(component.filteredFlakyTests()[0].flakinessScore).toBe(45);
    });

    it('should filter by severity low', () => {
      component.setSeverityFilter('low');
      fixture.detectChanges();
      expect(component.filteredFlakyTests().length).toBe(1);
      expect(component.filteredFlakyTests()[0].flakinessScore).toBe(15);
    });
  });

  describe('pagination', () => {
    it('should paginate results', () => {
      component.pageSize.set(2);
      fixture.detectChanges();
      expect(component.paginatedFlakyTests().length).toBe(2);
      expect(component.totalRecords()).toBe(3);
    });

    it('should update page on onPageChange', () => {
      component.onPageChange({ page: 1, rows: 10, first: 10, pageCount: 1 });
      expect(component.currentPage()).toBe(1);
      expect(component.pageSize()).toBe(10);
    });
  });

  describe('onSearchChange', () => {
    it('should update searchTerm immediately', () => {
      component.onSearchChange('foo');
      expect(component.searchTerm()).toBe('foo');
    });

    it('should reset page to 0 after debounce', async () => {
      component.currentPage.set(1);
      component.onSearchChange('foo');
      await new Promise(resolve => setTimeout(resolve, 350));
      expect(component.currentPage()).toBe(0);
    });
  });
});
