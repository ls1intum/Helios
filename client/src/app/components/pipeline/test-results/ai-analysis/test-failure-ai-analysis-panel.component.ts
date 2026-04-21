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
  imports: [ButtonModule, DialogModule, MessageModule, TagModule, TablerIconComponent],
  providers: [provideTablerIcons({ IconProgress })],
  templateUrl: './test-failure-ai-analysis-panel.component.html',
})
export class TestFailureAiAnalysisPanelComponent {
  repositoryId = input<number | null>(null);

  isVisible = false;
  status = signal<AnalysisStatus>('idle');
  errorMessage = signal<string | null>(null);
  response = signal<TestFailureAnalysisResponseDto | null>(null);
  analyzedTestCaseName = signal<string>('');

  open(testCase: TestCaseDto): void {
    if (this.isSubmitting()) return;
    this.resetState();
    this.analyzedTestCaseName.set(testCase.name);
    this.isVisible = true;
    void this.runAnalysis(testCase);
  }

  private async runAnalysis(testCase: TestCaseDto): Promise<void> {
    const repositoryId = this.repositoryId();
    if (repositoryId === null) return;

    this.status.set('submitting');
    try {
      const { data: result } = await analyzeFailedTest({
        path: { repositoryId, testCaseId: testCase.id },
        throwOnError: true,
      });

      this.response.set(result);
      if (result.status === 'FAILED') {
        this.status.set('failed');
        this.errorMessage.set(result.errorMessage ?? 'AI analysis failed.');
      } else {
        this.status.set('completed');
      }
    } catch {
      this.status.set('failed');
      this.errorMessage.set('Could not start AI analysis. Please try again.');
    }
  }

  onDialogHide(): void {
    this.resetState();
  }

  private resetState(): void {
    this.status.set('idle');
    this.response.set(null);
    this.errorMessage.set(null);
    this.analyzedTestCaseName.set('');
  }

  isSubmitting(): boolean {
    return this.status() === 'submitting';
  }

  formatConfidence(confidence: number | null | undefined): string {
    if (confidence == null) return 'N/A';
    return `${Math.round(confidence * 100)}%`;
  }
}
