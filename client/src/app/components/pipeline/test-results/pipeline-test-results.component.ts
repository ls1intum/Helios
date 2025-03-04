import { Component, computed, input, signal, viewChild } from '@angular/core';
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
import { injectQuery } from '@tanstack/angular-query-experimental';
import { getLatestTestResultsByBranchOptions, getLatestTestResultsByPullRequestIdOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { Popover, PopoverModule } from 'primeng/popover';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { FormsModule } from '@angular/forms';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { getLatestTestResultsByBranchOptions, getLatestTestResultsByPullRequestIdOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';

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
  ],
  templateUrl: './pipeline-test-results.component.html',
})
export class PipelineTestResultsComponent {
  selector = input<PipelineSelector | null>();

  testSuiteRows = signal(10);
  testSuiteFirst = signal(0);

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
    ...getLatestTestResultsByBranchOptions({ query: { branch: this.branchName()! } }),
    enabled: this.branchName() !== null,
    refetchInterval: 15000,
  }));

  pullRequestQuery = injectQuery(() => ({
    ...getLatestTestResultsByPullRequestIdOptions({ path: { pullRequestId: this.pullRequestId() || 0 } }),
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
    ...getLatestTestResultsByBranchOptions({ query: { branch: this.branchName()! } }),
    enabled: this.branchName() !== null,
    refetchInterval: 15000,
  }));

  pullRequestQuery = injectQuery(() => ({
    ...getLatestTestResultsByPullRequestIdOptions({ path: { pullRequestId: this.pullRequestId() || 0 } }),
    enabled: this.pullRequestId() !== null,
    refetchInterval: 15000,
  }));

  resultsQuery = computed(() => {
    return this.branchName() ? this.branchQuery : this.pullRequestQuery;
  });

  testSuites = computed(() => {
    const suites = this.resultsQuery().data()?.testSuites || [];

    // Let's show updated test suites first and then failed ones
    return suites.sort((a, b) => {
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

  suiteHasUpdates = (suite: TestSuiteDto) => {
    return suite.testCases.some(testCase => testCase.status !== testCase.previousStatus && testCase.previousStatus);
  };

  resultsHaveUpdated = computed(() => {
    return this.testSuites().some(this.suiteHasUpdates);
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

  // We want to show some kind of loading indicator in case all test workflows are still running
  // but already show the results of the completed ones
  isProcessing = computed(() => {
    return !!this.resultsQuery().data()?.isProcessing;
  });

  hasTestSuites = computed(() => {
    return this.testSuites().length > 0;
  });

  totalStats = computed(() => {
    const testSuites = this.testSuites();
    const totalStats = {
      total: testSuites.length,
      passed: testSuites.reduce((acc, suite) => acc + suite.tests - suite.failures - suite.errors - suite.skipped, 0),
      failures: testSuites.reduce((acc, suite) => acc + suite.errors + suite.failures, 0),
      skipped: testSuites.reduce((acc, suite) => acc + suite.skipped, 0),
      time: testSuites.reduce((acc, suite) => acc + suite.time, 0) * 1000,
    };
    return totalStats;
  });

  onPageChange = (event: PaginatorState) => {
    this.testSuiteFirst.set(event.first || 0);
    this.testSuiteRows.set(event.rows || 10);
  };
}
