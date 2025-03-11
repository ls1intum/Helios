import { Component, computed, input, signal, viewChild, effect } from '@angular/core';
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
import { TestSuiteDto } from '@app/core/modules/openapi';
import { Popover, PopoverModule } from 'primeng/popover';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { FormsModule } from '@angular/forms';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { getLatestGroupedTestResultsByBranchOptions, getLatestGroupedTestResultsByPullRequestIdOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { TabViewModule } from 'primeng/tabview';

// Interface for our internal workflow representation
interface WorkflowResult {
  name: string;
  workflowId: number;
  testSuites: TestSuiteDto[];
  isProcessing?: boolean;
}

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
  ],
  templateUrl: './pipeline-test-results.component.html',
})
export class PipelineTestResultsComponent {
  selector = input<PipelineSelector | null>();

  testSuiteRows = signal(10);
  testSuiteFirst = signal(0);
  activeWorkflowTab = signal(0);

  isTestResultsCollapsed = true;

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
    ...getLatestGroupedTestResultsByBranchOptions({ query: { branch: this.branchName()! } }),
    enabled: this.branchName() !== null,
    refetchInterval: 15000,
  }));

  pullRequestQuery = injectQuery(() => ({
    ...getLatestGroupedTestResultsByPullRequestIdOptions({ path: { pullRequestId: this.pullRequestId() || 0 } }),
    enabled: this.pullRequestId() !== null,
    refetchInterval: 15000,
  }));

  resultsQuery = computed(() => {
    return this.branchName() ? this.branchQuery : this.pullRequestQuery;
  });

  op = viewChild.required<Popover>('op');
  searchValue = signal<string>('');
  showOnlyFailed = signal<boolean>(false);

  toggleFilterMenu(event: Event) {
    this.op().toggle(event);
  }

  toggleShowOnlyFailed() {
    this.showOnlyFailed.set(!this.showOnlyFailed());
  }

  workflowResults = computed<WorkflowResult[]>(() => {
    const results = this.resultsQuery().data()?.testResults || {};
    return Object.entries(results).map(([key, value]) => ({
      name: key,
      workflowId: value.workflowId,
      testSuites: value.testSuites,
      isProcessing: value.isProcessing,
    }));
  });

  // Cache workflow failure and update status to avoid recalculating for every sort
  workflowHasFailures = (workflow: WorkflowResult): boolean => {
    return workflow.testSuites.some(suite => suite.failures > 0 || suite.errors > 0);
  };

  workflowHasUpdates = (workflow: WorkflowResult): boolean => {
    return workflow.testSuites.some(this.suiteHasUpdates);
  };

  // Optimize sorted workflows by only sorting when the underlying data changes
  sortedWorkflows = computed(() => {
    return this.workflowResults().sort((a, b) => {
      // Workflows with failures first
      const aHasFailures = this.workflowHasFailures(a);
      const bHasFailures = this.workflowHasFailures(b);

      if (aHasFailures && !bHasFailures) return -1;
      if (!aHasFailures && bHasFailures) return 1;

      // Then workflows with updates
      const aHasUpdates = this.workflowHasUpdates(a);
      const bHasUpdates = this.workflowHasUpdates(b);

      if (aHasUpdates && !bHasUpdates) return -1;
      if (!aHasUpdates && bHasUpdates) return 1;

      // Finally alphabetical
      return a.name.localeCompare(b.name);
    });
  });

  // Make sure the active tab is updated when workflows change
  constructor() {
    effect(() => {
      const workflows = this.sortedWorkflows();
      if (workflows.length === 0) return;

      // Make sure the active tab is in range
      const currentTab = this.activeWorkflowTab();
      if (currentTab >= workflows.length) {
        this.activeWorkflowTab.set(workflows.length - 1);
      }
    });
  }

  // Get the currently active workflow based on tab selection
  activeWorkflow = computed(() => {
    const workflows = this.sortedWorkflows();
    if (workflows.length === 0) return null;

    // Make sure the active tab is in range, but don't update the signal here
    const index = Math.min(this.activeWorkflowTab(), workflows.length - 1);
    return workflows[index];
  });

  // Ensure activeWorkflowTab is valid when workflows change
  effectiveWorkflowTab = computed(() => {
    const maxIndex = this.sortedWorkflows().length - 1;
    return Math.min(this.activeWorkflowTab(), Math.max(0, maxIndex));
  });

  testSuites = computed(() => {
    const workflow = this.activeWorkflow();
    if (!workflow) return [];

    // Sort suites within the workflow, updates first, then failures
    return [...workflow.testSuites].sort((a, b) => {
      if (this.suiteHasUpdates(a)) {
        return -1;
      }
      if (this.suiteHasUpdates(b)) {
        return 1;
      }
      if (a.failures > 0 || a.errors > 0) {
        return -1;
      }
      if (b.failures > 0 || b.errors > 0) {
        return 1;
      }
      return 0;
    });
  });

  filteredTestSuites = computed(() => {
    const testSuites = this.testSuites();
    const searchValue = this.searchValue().toLowerCase();
    const showOnlyFailed = this.showOnlyFailed();

    return testSuites.filter(suite => {
      if (showOnlyFailed && suite.failures + suite.errors === 0) {
        return false;
      }

      if (searchValue) {
        return suite.name.toLowerCase().includes(searchValue) || suite.testCases.some(testCase => testCase.name.toLowerCase().includes(searchValue));
      }

      return true;
    });
  });

  sortTestCases(cases: TestSuiteDto['testCases']) {
    // We want to show updated ones before the others
    // and then failed ones before the others
    return cases.sort((a, b) => {
      if (a.status !== a.previousStatus && a.previousStatus) {
        return -1;
      }
      if (b.status !== b.previousStatus && b.previousStatus) {
        return 1;
      }
      if (a.status === 'FAILED' || a.status === 'ERROR') {
        return -1;
      }
      if (b.status === 'FAILED' || b.status === 'ERROR') {
        return 1;
      }
      return 0;
    });
  }

  onTabChange(event: number) {
    // Only update if the index is valid
    if (event >= 0 && event < this.sortedWorkflows().length) {
      this.activeWorkflowTab.set(event);
      // Reset pagination when changing tabs
      this.testSuiteFirst.set(0);
    }
  }

  suiteHasUpdates = (suite: TestSuiteDto) => {
    return suite.testCases.some(testCase => testCase.status !== testCase.previousStatus && testCase.previousStatus);
  };

  // Check if any test suite in any workflow has updates
  resultsHaveUpdated = computed(() => {
    return this.workflowResults().some(workflow => workflow.testSuites.some(this.suiteHasUpdates));
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
  workflowState = (workflow: WorkflowResult) => {
    if (workflow.testSuites.some(suite => suite.failures > 0 || suite.errors > 0)) {
      return 'FAILURE';
    }

    if (workflow.testSuites.every(suite => suite.skipped === suite.tests)) {
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
    return this.workflowResults().some(workflow => workflow.testSuites.length > 0);
  });

  totalStats = computed(() => {
    const workflows = this.workflowResults();
    const allSuites = workflows.flatMap(workflow => workflow.testSuites);

    const totalStats = {
      total: allSuites.length,
      passed: allSuites.reduce((acc, suite) => acc + suite.tests - suite.failures - suite.errors - suite.skipped, 0),
      failures: allSuites.reduce((acc, suite) => acc + suite.errors + suite.failures, 0),
      skipped: allSuites.reduce((acc, suite) => acc + suite.skipped, 0),
      time: allSuites.reduce((acc, suite) => acc + suite.time, 0) * 1000,
    };
    return totalStats;
  });

  // Stats for the current workflow tab
  workflowStats = computed(() => {
    const workflow = this.activeWorkflow();
    if (!workflow) return null;

    const suites = workflow.testSuites;
    return {
      total: suites.length,
      passed: suites.reduce((acc, suite) => acc + suite.tests - suite.failures - suite.errors - suite.skipped, 0),
      failures: suites.reduce((acc, suite) => acc + suite.errors + suite.failures, 0),
      skipped: suites.reduce((acc, suite) => acc + suite.skipped, 0),
      time: suites.reduce((acc, suite) => acc + suite.time, 0) * 1000,
    };
  });

  onPageChange = (event: PaginatorState) => {
    this.testSuiteFirst.set(event.first || 0);
    this.testSuiteRows.set(event.rows || 10);
  };
}
