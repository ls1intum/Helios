import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { Component, input, signal } from '@angular/core';
import { analyzeFailedTest, getFailureAnalysisUsage, getLatestCachedFailureAnalysis, TestCaseDto, TestFailureAnalysisUsageDto } from '@app/core/modules/openapi';
import type { TestFailureAnalysisResponseDto } from '@app/core/modules/openapi';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { MessageModule } from 'primeng/message';
import { TagModule } from 'primeng/tag';
import { TablerIconComponent, provideTablerIcons } from 'angular-tabler-icons';
import { IconProgress } from 'angular-tabler-icons/icons';

type AnalysisStatus = 'idle' | 'submitting' | 'completed' | 'failed';

@Component({
  selector: 'app-test-failure-ai-analysis-panel',
  imports: [ButtonModule, DatePipe, DialogModule, MessageModule, NgTemplateOutlet, TagModule, TablerIconComponent],
  providers: [provideTablerIcons({ IconProgress })],
  templateUrl: './test-failure-ai-analysis-panel.component.html',
})
export class TestFailureAiAnalysisPanelComponent {
  repositoryId = input<number | null>(null);

  isPreparingVisible = false;
  isConfirmationVisible = false;
  isRegenerateConfirmationVisible = false;
  isVisible = false;
  usage = signal<TestFailureAnalysisUsageDto | null>(null);
  isUsageLoading = signal<boolean>(false);
  usageErrorMessage = signal<string | null>(null);
  pendingTestCase = signal<TestCaseDto | null>(null);
  status = signal<AnalysisStatus>('idle');
  errorMessage = signal<string | null>(null);
  response = signal<TestFailureAnalysisResponseDto | null>(null);
  analyzedTestCase = signal<TestCaseDto | null>(null);

  open(testCase: TestCaseDto): void {
    if (this.isSubmitting() || this.isPreparingVisible || this.isConfirmationVisible) return;
    this.resetUsageState();
    this.resetAnalysisState();
    this.pendingTestCase.set(testCase);
    this.isPreparingVisible = true;
    void this.loadUsage();
    void this.openWithCacheAwareFlow(testCase);
  }

  private async openWithCacheAwareFlow(testCase: TestCaseDto): Promise<void> {
    const cachedResponse = await this.loadLatestCachedAnalysis(testCase);
    const currentPendingTestCase = this.pendingTestCase();
    this.isPreparingVisible = false;

    if (!currentPendingTestCase || currentPendingTestCase.id !== testCase.id) {
      return;
    }

    if (cachedResponse?.result) {
      this.analyzedTestCase.set(testCase);
      this.response.set(cachedResponse);
      this.status.set('completed');
      this.pendingTestCase.set(null);
      this.isVisible = true;
      return;
    }

    this.isConfirmationVisible = true;
  }

  cancelPreparation(): void {
    this.isPreparingVisible = false;
    this.pendingTestCase.set(null);
    this.resetUsageState();
  }

  async confirmAnalysis(): Promise<void> {
    const testCase = this.pendingTestCase();
    if (!testCase || this.isSubmitting()) {
      return;
    }

    this.isConfirmationVisible = false;
    this.analyzedTestCase.set(testCase);
    this.isVisible = true;
    this.pendingTestCase.set(null);
    await this.runAnalysis(testCase, false);
    void this.loadUsage();
  }

  cancelConfirmation(): void {
    this.isConfirmationVisible = false;
    this.pendingTestCase.set(null);
    this.resetUsageState();
  }

  async regenerateAnalysis(): Promise<void> {
    const testCase = this.analyzedTestCase();
    if (!testCase || this.isSubmitting()) {
      return;
    }

    await this.runAnalysis(testCase, true);
    void this.loadUsage();
  }

  requestRegenerateConfirmation(): void {
    if (this.isSubmitting() || this.isUsageExhausted() || !this.canRegenerateAnalysis()) {
      return;
    }
    this.isRegenerateConfirmationVisible = true;
    void this.loadUsage();
  }

  async confirmRegenerateAnalysis(): Promise<void> {
    if (this.isSubmitting() || this.isUsageExhausted()) {
      return;
    }
    this.isRegenerateConfirmationVisible = false;
    await this.regenerateAnalysis();
  }

  cancelRegenerateConfirmation(): void {
    this.isRegenerateConfirmationVisible = false;
  }

  onRegenerateDialogHide(): void {
    this.cancelRegenerateConfirmation();
  }

  private async runAnalysis(testCase: TestCaseDto, regenerate: boolean): Promise<void> {
    const repositoryId = this.repositoryId();
    if (repositoryId === null) return;

    const previousResponse = this.response();
    this.status.set('submitting');
    this.errorMessage.set(null);
    try {
      const { data: result } = await analyzeFailedTest({
        path: { repositoryId, testCaseId: testCase.id },
        query: { regenerate },
        throwOnError: true,
      });

      if (result.status === 'FAILED') {
        if (regenerate && previousResponse?.result) {
          this.status.set('completed');
          this.errorMessage.set(result.errorMessage ?? 'Could not regenerate analysis. Showing the previous result.');
          this.response.set(previousResponse);
        } else {
          this.response.set(result);
          this.status.set('failed');
          this.errorMessage.set(result.errorMessage ?? 'AI analysis failed.');
        }
      } else {
        this.response.set(result);
        this.status.set('completed');
      }
    } catch {
      if (regenerate && previousResponse?.result) {
        this.response.set(previousResponse);
        this.status.set('completed');
        this.errorMessage.set('Could not regenerate analysis. Showing the previous result.');
      } else {
        this.status.set('failed');
        this.errorMessage.set('Could not start AI analysis. Please try again.');
      }
    }
  }

  private async loadUsage(): Promise<void> {
    this.usage.set(null);
    this.usageErrorMessage.set(null);
    this.isUsageLoading.set(true);

    try {
      const { data } = await getFailureAnalysisUsage({
        throwOnError: true,
      });
      this.usage.set(data);
    } catch {
      this.usageErrorMessage.set('Could not load current AI usage limits. You can still continue.');
    } finally {
      this.isUsageLoading.set(false);
    }
  }

  private async loadLatestCachedAnalysis(testCase: TestCaseDto): Promise<TestFailureAnalysisResponseDto | null> {
    const repositoryId = this.repositoryId();
    if (repositoryId === null) return null;

    try {
      const { data } = await getLatestCachedFailureAnalysis({
        path: { repositoryId, testCaseId: testCase.id },
        throwOnError: true,
      });
      if (!data.hasCachedResult || !data.cachedResult) {
        return null;
      }
      return data.cachedResult;
    } catch {
      return null;
    }
  }

  onConfirmationDialogHide(): void {
    if (this.pendingTestCase()) {
      this.cancelConfirmation();
    }
  }

  onDialogHide(): void {
    this.resetAnalysisState();
  }

  private resetUsageState(): void {
    this.usage.set(null);
    this.usageErrorMessage.set(null);
    this.isUsageLoading.set(false);
  }

  private resetAnalysisState(): void {
    this.status.set('idle');
    this.response.set(null);
    this.errorMessage.set(null);
    this.analyzedTestCase.set(null);
    this.isVisible = false;
  }

  isSubmitting(): boolean {
    return this.status() === 'submitting';
  }

  canRegenerateAnalysis(): boolean {
    return this.response()?.status === 'COMPLETED';
  }

  formatConfidence(confidence: number | null | undefined): string {
    if (confidence == null) return 'N/A';
    return `${Math.round(confidence * 100)}%`;
  }

  formatUsage(used: number | undefined, limit: number | undefined): string {
    if (used == null || limit == null) {
      return 'N/A';
    }
    return `${used} / ${limit} analyses used`;
  }

  formatBurstWindow(windowSeconds: number | undefined): string {
    if (!windowSeconds || windowSeconds <= 0) {
      return 'N/A';
    }
    const minutes = Math.max(1, Math.round(windowSeconds / 60));
    return `${minutes}m`;
  }

  isShowingCachedResult(): boolean {
    return this.response()?.cacheHit === true && this.status() === 'completed';
  }

  isUsageExhausted(): boolean {
    const usage = this.usage();
    if (!usage || usage.rateLimitEnabled !== true) return false;
    return this.isLimitExhausted(usage.dailyUsed, usage.dailyLimit) || this.isLimitExhausted(usage.burstUsed, usage.burstLimit);
  }

  shouldShowUsageLimits(): boolean {
    return this.usage()?.rateLimitEnabled === true;
  }

  private isLimitExhausted(used: number | undefined, limit: number | undefined): boolean {
    if (limit == null || limit <= 0 || used == null) {
      return false;
    }
    return used >= limit;
  }
}
