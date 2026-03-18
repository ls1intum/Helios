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

const mockOverview = {
  summary: {
    totalTrackedTests: 10,
    flakyTestCount: 3,
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

    // Mock TanStack query for testing
    component.query = {
      ...component.query,
      data: signal(mockOverview),
      isPending: signal(false),
      isError: signal(false),
    } as unknown as typeof component.query;

    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should expose flaky tests from query data', () => {
    expect(component.query.data()).toEqual(mockOverview);
    expect(component.flakyTests().length).toBe(3);
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
});
