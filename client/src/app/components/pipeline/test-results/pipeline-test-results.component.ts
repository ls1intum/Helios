import { Component, computed, input, signal } from '@angular/core';
import { TableModule } from 'primeng/table';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { PanelModule } from 'primeng/panel';
import { IconsModule } from 'icons.module';
import { TooltipModule } from 'primeng/tooltip';
import { SkeletonModule } from 'primeng/skeleton';
import { Pipeline } from '../pipeline.component';
import { TagModule } from 'primeng/tag';
import { PaginatorModule, PaginatorState } from 'primeng/paginator';
import { CommonModule, DatePipe } from '@angular/common';
import { TestSuiteDto } from '@app/core/modules/openapi';

@Component({
  selector: 'app-pipeline-test-results',
  imports: [TableModule, ProgressSpinnerModule, PanelModule, IconsModule, TooltipModule, SkeletonModule, TagModule, PaginatorModule, DatePipe, CommonModule],
  templateUrl: './pipeline-test-results.component.html',
})
export class PipelineTestResultsComponent {
  pipeline = input.required<Pipeline>();

  testSuiteRows = signal(10);
  testSuiteFirst = signal(0);

  isTestResultsCollapsed = true;

  testSuites = computed(() => {
    const testSuites = this.pipeline()
      .groups.flatMap(group => group.workflows.flatMap(workflow => workflow.testSuites))
      // We want to show the test suites with the most failures/errors first
      .sort((a, b) => b.failures + b.errors - (a.failures + a.errors));
    return testSuites;
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
    const p = this.testWorkflows().some(workflow => workflow.testProcessingStatus === 'PROCESSING');
    console.log('isProcessing', p);
    return p;
  });

  testWorkflows = computed(() => {
    return this.pipeline()
      .groups.flatMap(group => group.workflows)
      .filter(workflow => workflow.label === 'TEST');
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
