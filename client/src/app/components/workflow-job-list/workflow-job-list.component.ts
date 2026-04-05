import { CommonModule, DatePipe } from '@angular/common';
import { Component, inject, input, signal } from '@angular/core';
import { WorkflowJobDto } from '@app/core/modules/openapi';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconCircleCheck, IconCircleMinus, IconCircleX, IconClock, IconExternalLink, IconProgress, IconChevronDown, IconChevronRight } from 'angular-tabler-icons/icons';
import { Button } from 'primeng/button';
import { SkeletonModule } from 'primeng/skeleton';
import { getStatusColors, getStatusIconClasses } from '@app/core/utils/status-colors';

@Component({
  selector: 'app-workflow-job-list',
  standalone: true,
  imports: [CommonModule, TablerIconComponent, Button, SkeletonModule],
  providers: [
    DatePipe,
    provideTablerIcons({
      IconClock,
      IconProgress,
      IconCircleMinus,
      IconCircleCheck,
      IconCircleX,
      IconExternalLink,
      IconChevronDown,
      IconChevronRight,
    }),
  ],
  templateUrl: './workflow-job-list.component.html',
})
export class WorkflowJobListComponent {
  jobs = input.required<WorkflowJobDto[]>();
  isPending = input(false);
  isError = input(false);

  private datePipe = inject(DatePipe);

  expandedJobs = signal<Record<string, boolean>>({});

  toggleJobExpansion(jobId: number) {
    this.expandedJobs.update(state => ({
      ...state,
      [jobId]: !state[jobId],
    }));
  }

  isJobExpanded(jobId: number): boolean {
    return !!this.expandedJobs()[jobId];
  }

  getStatusClass(status: string | null | undefined, conclusion: string | null | undefined): string {
    return getStatusColors(conclusion, status).badge;
  }

  getStatusIndicatorClass(status: string | null | undefined, conclusion: string | null | undefined): string {
    return getStatusColors(conclusion, status).indicator;
  }

  getStatusIcon(status: string | null | undefined, conclusion: string | null | undefined): string {
    if (conclusion === 'success') return 'circle-check';
    if (conclusion === 'failure') return 'circle-x';
    if (conclusion === 'skipped' || conclusion === 'cancelled') return 'circle-minus';
    if (status === 'in_progress') return 'progress';
    if (status === 'queued' || status === 'waiting') return 'clock';
    return 'help';
  }

  getIconColorClass(status: string | null | undefined, conclusion: string | null | undefined): string {
    return getStatusIconClasses(conclusion, status);
  }

  getStatusText(status: string | null | undefined, conclusion: string | null | undefined): string {
    return conclusion || status || 'Unknown';
  }

  formatTime(timestamp: string | null | undefined): string {
    if (!timestamp) return '';
    return this.datePipe.transform(timestamp, 'HH:mm:ss') || '';
  }

  getDuration(startTime: string | undefined, endTime: string | undefined): string {
    if (!startTime) return '';
    const start = new Date(startTime).getTime();
    const end = endTime ? new Date(endTime).getTime() : Date.now();
    const seconds = Math.floor((end - start) / 1000);
    if (seconds < 60) return `${seconds}s`;
    if (seconds < 3600) {
      return `${Math.floor(seconds / 60)}m ${seconds % 60}s`;
    }
    return `${Math.floor(seconds / 3600)}h ${Math.floor((seconds % 3600) / 60)}m`;
  }

  openLink(url: string | undefined) {
    if (url) {
      window.open(url, '_blank');
    }
  }
}
