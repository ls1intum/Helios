import { DatePipe } from '@angular/common';
import { Component, input, signal } from '@angular/core';
import { analyzeFailedTest, TestCaseDto } from '@app/core/modules/openapi';
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
  imports: [ButtonModule, DatePipe, DialogModule, MessageModule, TagModule, TablerIconComponent],
  providers: [provideTablerIcons({ IconProgress })],
  templateUrl: './test-failure-ai-analysis-panel.component.html',
})
export class TestFailureAiAnalysisPanelComponent {
  repositoryId = input<number | null>(null);

  isVisible = false;
  status = signal<AnalysisStatus>('idle');
  errorMessage = signal<string | null>(null);
  response = signal<TestFailureAnalysisResponseDto | null>(null);
  analyzedTestCase = signal<TestCaseDto | null>(null);

  open(testCase: TestCaseDto): void {
    if (this.isSubmitting()) return;
    this.resetState();
    this.analyzedTestCase.set(testCase);
    this.isVisible = true;
    void this.runAnalysis(testCase, false);
  }

  async regenerateAnalysis(): Promise<void> {
    const testCase = this.analyzedTestCase();
    if (!testCase || this.isSubmitting()) {
      return;
    }

    await this.runAnalysis(testCase, true);
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

  onDialogHide(): void {
    this.resetState();
  }

  private resetState(): void {
    this.status.set('idle');
    this.response.set(null);
    this.errorMessage.set(null);
    this.analyzedTestCase.set(null);
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
}
