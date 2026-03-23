import { CommonModule, DatePipe } from '@angular/common';
import { Component, inject, input, signal } from '@angular/core';
import { WorkflowJobDto } from '@app/core/modules/openapi';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import {
  IconBrandGithub,
  IconCircleCheck,
  IconCircleMinus,
  IconCircleX,
  IconClock,
  IconExternalLink,
  IconProgress,
  IconChevronDown,
  IconChevronRight,
} from 'angular-tabler-icons/icons';
import { Button } from 'primeng/button';
import { SkeletonModule } from 'primeng/skeleton';

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
      IconBrandGithub,
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
    if (conclusion === 'success') return 'text-green-600 bg-green-100 dark:text-green-400 dark:bg-green-900/30';
    if (conclusion === 'failure') return 'text-red-600 bg-red-100 dark:text-red-400 dark:bg-red-900/30';
    if (conclusion === 'skipped') return 'text-gray-600 bg-gray-100 dark:text-gray-400 dark:bg-gray-900';
    if (conclusion === 'cancelled') return 'text-orange-600 bg-orange-50 dark:text-orange-400 dark:bg-orange-900/30';
    if (status === 'in_progress') return 'text-blue-600 bg-blue-50 dark:text-blue-400 dark:bg-blue-900/30';
    if (status === 'queued' || status === 'waiting') return 'text-gray-600 bg-gray-100 dark:text-gray-400 dark:bg-gray-900';
    return 'text-gray-600 dark:text-gray-400';
  }

  getStatusIndicatorClass(status: string | null | undefined, conclusion: string | null | undefined): string {
    if (conclusion === 'success') return 'bg-green-500 dark:bg-green-400';
    if (conclusion === 'failure') return 'bg-red-500 dark:bg-red-400';
    if (conclusion === 'skipped') return 'bg-gray-400 dark:bg-gray-500';
    if (conclusion === 'cancelled') return 'bg-orange-500 dark:bg-orange-400';
    if (status === 'in_progress') return 'bg-blue-500 dark:bg-blue-400';
    if (status === 'queued' || status === 'waiting') return 'bg-gray-300 dark:bg-gray-600';
    return 'bg-gray-300 dark:bg-gray-600';
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
    if (conclusion === 'success') return 'text-green-600 dark:text-green-400';
    if (conclusion === 'failure') return 'text-red-600 dark:text-red-400';
    if (conclusion === 'skipped') return 'text-gray-600 dark:text-gray-400';
    if (conclusion === 'cancelled') return 'text-orange-600 dark:text-orange-400';
    if (status === 'in_progress') return 'text-blue-600 dark:text-blue-400 animate-spin';
    if (status === 'queued' || status === 'waiting') return 'text-gray-600 dark:text-gray-400';
    return 'text-gray-600 dark:text-gray-400';
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
