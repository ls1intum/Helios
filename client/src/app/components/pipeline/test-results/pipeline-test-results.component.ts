import { Component, computed, input, signal, viewChild, effect, ViewChild, ElementRef } from '@angular/core';
import { TableModule } from 'primeng/table';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { PanelModule } from 'primeng/panel';
import { IconsModule } from 'icons.module';
import { TooltipModule } from 'primeng/tooltip';
import { SkeletonModule } from 'primeng/skeleton';
import { PipelineSelector } from '../pipeline.component';
import { TagModule } from 'primeng/tag';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { CommonModule, DatePipe } from '@angular/common';
import { Popover, PopoverModule } from 'primeng/popover';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { FormsModule } from '@angular/forms';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { TabViewModule } from 'primeng/tabview';
import { getLatestTestResultsByBranchOptions, getLatestTestResultsByPullRequestIdOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { TestCaseDto, TestSuiteDto, TestTypeResults } from '@app/core/modules/openapi';
import { DialogModule } from 'primeng/dialog';
import { SliderModule } from 'primeng/slider';

// Define log level interface and constants
interface LogLevel {
  value: number;
  name: string;
  label: string;
  color: string;
  includes: string[];
}

const LOG_LEVELS: LogLevel[] = [
  { value: 0, name: 'OFF', label: 'OFF', color: '', includes: ['OFF'] },
  { value: 1, name: 'FATAL', label: 'FATAL+', color: 'text-red-800', includes: ['FATAL', 'OFF'] },
  { value: 2, name: 'ERROR', label: 'ERROR+', color: 'text-red-600', includes: ['ERROR', 'FATAL', 'OFF'] },
  { value: 3, name: 'WARN', label: 'WARN+', color: 'text-amber-600', includes: ['WARN', 'ERROR', 'FATAL', 'OFF'] },
  { value: 4, name: 'INFO', label: 'INFO+', color: 'text-blue-600', includes: ['INFO', 'WARN', 'ERROR', 'FATAL', 'OFF'] },
  { value: 5, name: 'DEBUG', label: 'DEBUG+', color: 'text-gray-600', includes: ['DEBUG', 'INFO', 'WARN', 'ERROR', 'FATAL', 'OFF'] },
  { value: 6, name: 'TRACE', label: 'TRACE+', color: 'text-gray-400', includes: ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'FATAL', 'OFF'] },
  { value: 7, name: 'ALL', label: 'ALL', color: '', includes: [] },
];

@Component({
  selector: 'app-pipeline-test-results',
  imports: [
    TableModule,
    ProgressSpinnerModule,
    PanelModule,
    IconsModule,
    TooltipModule,
    SkeletonModule,
    TagModule,
    PaginatorModule,
    DatePipe,
    CommonModule,
    PopoverModule,
    ButtonModule,
    InputTextModule,
    FormsModule,
    TabViewModule,
    DialogModule,
    SliderModule,
  ],
  templateUrl: './pipeline-test-results.component.html',
})
export class PipelineTestResultsComponent {
  selector = input<PipelineSelector | null>();

  testSuiteRows = signal(10);
  testSuiteFirst = signal(0);
  activeTestTypeTab = signal(0);

  isTestResultsCollapsed = true;

  showTestDetails = false;
  selectedTestCase = signal<(TestCaseDto & { suiteSystemOut: string | undefined }) | null>(null);

  // Log level filtering
  selectedLogLevelValue = signal<number>(2); // Default to ERROR

  // Get the current log level object based on the selected value
  selectedLogLevel = computed(() => {
    return LOG_LEVELS.find(level => level.value === this.selectedLogLevelValue()) || LOG_LEVELS[2];
  });

  // Get the array of log level names that should be included based on the selection
  includedLogLevels = computed(() => {
    const level = this.selectedLogLevel();
    return level.includes;
  });

  // Function to filter logs by level
  filterLogsByLevel(text: string | undefined): string {
    if (!text) return '';

    // If ALL is selected, return all logs
    if (this.selectedLogLevelValue() === 7) return text;

    const includedLevels = this.includedLogLevels();

    // Split on newlines, then filter each line
    return text
      .split('\n')
      .filter(line => {
        // Parse log level from the line if it exists
        // Look for patterns like "23:42:33.006 |  29 nPool-1-worker-3 | DEBUG | ..."
        // We are mathcing line that contains | <uppercase_text> |
        const match = line.match(/\|\s*([A-Z]+)\s*\|/);
        if (!match) return true; // Keep lines without log level

        const logLevel = match[1].trim(); // DEBUG, INFO, WARN, ERROR, FATAL, OFF
        return includedLevels.includes(logLevel);
      })
      .join('\n');
  }

  // Computed signals for filtered content (excluding stack trace)
  filteredTestCaseLogs = computed(() => {
    return this.filterLogsByLevel(this.selectedTestCase()?.systemOut);
  });

  filteredTestSuiteLogs = computed(() => {
    return this.filterLogsByLevel(this.selectedTestCase()?.suiteSystemOut);
  });

  // Helper function for log level styling (colors)
  getLogLevelClass(line: string): string {
    // Use the same regex pattern as in filterLogsByLevel
    const match = line.match(/\|\s*([A-Z]+)\s*\|/);
    if (!match) return '';

    const level = match[1].trim();
    const logLevel = LOG_LEVELS.find(l => l.name === level);
    return logLevel?.color || '';
  }

  // Helper methods for two-way binding with signals
  updateLogLevel(value: number) {
    this.selectedLogLevelValue.set(value);
  }

  getLogLevelValue() {
    return this.selectedLogLevelValue();
  }

  showFlakinessScoreInfo = false;

  showTestCaseDetails(testCase: TestCaseDto, testSuite: TestSuiteDto) {
    this.selectedTestCase.set({
      ...testCase,
      suiteSystemOut: testSuite.systemOut,
    });
    this.showTestDetails = true;
  }

  hasTestDetails(testCase: TestCaseDto, testSuite: TestSuiteDto): boolean {
    return !!(testCase.stackTrace || testCase.systemOut || testSuite.systemOut);
  }

  branchName = computed(() => {
    const selector = this.selector();
    if (!selector) return null;
    return 'branchName' in selector ? selector.branchName : null;
  });

  pullRequestId = computed(() => {
    const selector = this.selector();
    if (!selector) return null;
    return 'pullRequestId' in selector ? selector.pullRequestId : null;
  });

  branchQuery = injectQuery(() => ({
    ...getLatestTestResultsByBranchOptions({
      query: {
        branch: this.branchName()!,
        page: this.testSuiteFirst() / this.testSuiteRows(), // Convert to page number
        size: this.testSuiteRows(),
        search: this.searchValue(),
        onlyFailed: this.showOnlyFailed(),
      },
    }),
    enabled: this.branchName() !== null,
    // refetchInterval: 15000,
  }));

  pullRequestQuery = injectQuery(() => ({
    ...getLatestTestResultsByPullRequestIdOptions({
      query: {
        page: this.testSuiteFirst() / this.testSuiteRows(), // Convert to page number
        size: this.testSuiteRows(),
        search: this.searchValue(),
        onlyFailed: this.showOnlyFailed(),
      },
      path: { pullRequestId: this.pullRequestId() || 0 },
    }),
    enabled: this.pullRequestId() !== null,
    // refetchInterval: 15000,
  }));

  resultsQuery = computed(() => {
    return this.branchName() ? this.branchQuery : this.pullRequestQuery;
  });

  op = viewChild.required<Popover>('op');
  searchValue = signal<string>('');
  showOnlyFailed = signal<boolean>(false);

  @ViewChild('searchInput') searchInput!: ElementRef;
  private searchTimeout: ReturnType<typeof setTimeout> | null = null;

  onSearchChange(value: string) {
    // Clear any existing timeout
    if (this.searchTimeout) {
      clearTimeout(this.searchTimeout);
    }

    // Set a new timeout to update the search value
    this.searchTimeout = setTimeout(() => {
      this.searchValue.set(value);
      this.testSuiteFirst.set(0); // Reset pagination when search changes
    }, 1000); // 1s debounce
  }

  toggleFilterMenu(event: Event) {
    this.op().toggle(event);
  }

  toggleShowOnlyFailed() {
    this.showOnlyFailed.set(!this.showOnlyFailed());
    this.testSuiteFirst.set(0);
  }

  testTypeResults = computed<TestTypeResults[]>(() => {
    return this.resultsQuery().data()?.testResults || [];
  });

  // Cache workflow failure and update status to avoid recalculating for every sort
  testTypeHasFailures = (workflow: TestTypeResults): boolean => {
    return workflow.stats.failures > 0 || workflow.stats.errors > 0;
  };

  testTypeHasUpdates = (testType: TestTypeResults): boolean => {
    return testType.stats.totalUpdates > 0;
  };

  // Optimize sorted workflows by only sorting when the underlying data changes
  sortedTestTypes = computed(() => {
    return this.testTypeResults().sort((a, b) => {
      // Workflows with failures first
      const aHasFailures = this.testTypeHasFailures(a);
      const bHasFailures = this.testTypeHasFailures(b);

      if (aHasFailures && !bHasFailures) return -1;
      if (!aHasFailures && bHasFailures) return 1;

      // Then workflows with updates
      const aHasUpdates = this.testTypeHasUpdates(a);
      const bHasUpdates = this.testTypeHasUpdates(b);

      if (aHasUpdates && !bHasUpdates) return -1;
      if (!aHasUpdates && bHasUpdates) return 1;

      // Finally alphabetical
      return a.testTypeName.localeCompare(b.testTypeName);
    });
  });

  // Make sure the active tab is updated when workflows change
  constructor() {
    effect(() => {
      const testTypes = this.sortedTestTypes();
      if (testTypes.length === 0) return;

      // Make sure the active tab is in range
      const currentTab = this.activeTestTypeTab();
      if (currentTab >= testTypes.length) {
        this.activeTestTypeTab.set(testTypes.length - 1);
      }
    });
  }

  // Get the currently active workflow based on tab selection
  activeTestType = computed(() => {
    const testTypes = this.sortedTestTypes();
    if (testTypes.length === 0) return null;

    // Make sure the active tab is in range, but don't update the signal here
    const index = Math.min(this.activeTestTypeTab(), testTypes.length - 1);
    return testTypes[index];
  });

  // Ensure activeWorkflowTab is valid when workflows change
  effectiveTestTypeTab = computed(() => {
    const maxIndex = this.sortedTestTypes().length - 1;
    return Math.min(this.activeTestTypeTab(), Math.max(0, maxIndex));
  });

  testSuites = computed(() => {
    const activeTestType = this.activeTestType();
    if (!activeTestType) return [];
    return activeTestType.testSuites;
  });

  onTabChange(event: number) {
    // Only update if the index is valid
    if (event >= 0 && event < this.sortedTestTypes().length) {
      this.activeTestTypeTab.set(event);
      // Reset pagination when changing tabs
      this.testSuiteFirst.set(0);
    }
  }

  suiteHasUpdates = (suite: TestSuiteDto) => {
    return suite.testCases.some(testCase => testCase.status !== testCase.previousStatus && testCase.previousStatus);
  };

  // Check if any test suite in any workflow has updates
  resultsHaveUpdated = computed(() => {
    return this.testTypeResults().some(testTypeResults => testTypeResults.stats.totalUpdates > 0);
  });

  overallSuiteState = (suite: TestSuiteDto) => {
    if (suite.failures > 0 || suite.errors > 0) {
      return 'FAILURE';
    }

    if (suite.skipped === suite.tests) {
      return 'SKIPPED';
    }

    return 'SUCCESS';
  };

  // Workflow status badges
  testTypeState = (workflow: TestTypeResults) => {
    if (workflow.stats.failures > 0 || workflow.stats.errors > 0) {
      return 'FAILURE';
    }

    if (workflow.stats.skipped === workflow.stats.totalTests) {
      return 'SKIPPED';
    }

    return 'SUCCESS';
  };

  // We want to show some kind of loading indicator in case all test workflows are still running
  // but already show the results of the completed ones
  isProcessing = computed(() => {
    return !!this.resultsQuery().data()?.isProcessing;
  });

  hasTestSuites = computed(() => {
    return this.testTypeResults().some(workflow => workflow.stats.totalTests > 0);
  });

  totalStats = computed(() => {
    const allStats = this.testTypeResults().map(r => r.stats);

    const totalStats = {
      total: allStats.reduce((acc, stats) => acc + stats.totalSuites, 0),
      passed: allStats.reduce((acc, stats) => acc + stats.totalTests - stats.failures - stats.errors - stats.skipped, 0),
      failures: allStats.reduce((acc, stats) => acc + stats.errors + stats.failures, 0),
      skipped: allStats.reduce((acc, stats) => acc + stats.skipped, 0),
      time: allStats.reduce((acc, stats) => acc + stats.totalTime, 0) * 1000,
    };

    return totalStats;
  });

  // Stats for the current workflow tab
  activeTestTypeStats = computed(() => {
    const activeTestType = this.activeTestType();

    if (!activeTestType) return null;

    const stats = activeTestType.stats;

    return {
      total: stats.totalSuites,
      passed: stats.totalTests - stats.failures - stats.errors - stats.skipped,
      failures: stats.errors + stats.failures,
      skipped: stats.skipped,
      time: stats.totalTime * 1000,
    };
  });

  onPageChange = (event: PaginatorState) => {
    this.testSuiteFirst.set(event.first || 0);
    this.testSuiteRows.set(event.rows || 10);
  };

  formatFailureRate = (failureRate: number | undefined) => {
    if (failureRate === undefined) return 'N/A';
    return Math.floor(failureRate * 100).toString();
  };

  formatFlakinessScore = (flakinessScore: number | undefined) => {
    if (flakinessScore === undefined) return 'N/A';
    return Math.floor(flakinessScore).toString();
  };
}
