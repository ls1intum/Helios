import { Component, computed, input, signal } from '@angular/core';
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

@Component({
  selector: 'app-pipeline-test-results',
  imports: [TableModule, ProgressSpinnerModule, PanelModule, IconsModule, TooltipModule, SkeletonModule, TagModule, PaginatorModule, DatePipe, CommonModule],
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

  testSuites = computed(() => {
    return this.resultsQuery().data()?.testSuites || [];
  });

  sortTestCases(cases: TestSuiteDto['testCases']) {
    // We want to show the failed/error test cases first
    return cases.sort(a => (a.status === 'ERROR' || a.status === 'FAILED' ? -1 : 1));
  }

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
