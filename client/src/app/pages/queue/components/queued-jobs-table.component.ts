import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { QueuedReasonChipComponent } from './queued-reason-chip.component';
import type { QueuedJob } from '../queue.api';

@Component({
  selector: 'app-queued-jobs-table',
  standalone: true,
  imports: [TableModule, TagModule, QueuedReasonChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p-table
      [value]="jobs()"
      [scrollable]="true"
      scrollHeight="480px"
      dataKey="jobId"
      [virtualScroll]="jobs().length > 50"
      [virtualScrollItemSize]="44"
    >
      <ng-template pTemplate="header">
        <tr>
          <th>Workflow / Job</th>
          <th>Branch</th>
          <th>Labels</th>
          <th class="text-right">Wait</th>
          <th class="text-right">ETA</th>
          <th>Reason</th>
        </tr>
      </ng-template>
      <ng-template pTemplate="body" let-job>
        <tr [class.text-red-500]="job.isStuck">
          <td>
            <div class="font-medium">{{ job.workflowName }}</div>
            <div class="text-xs text-surface-500">{{ job.jobName }}</div>
          </td>
          <td>{{ job.headBranch }}</td>
          <td>
            <div class="flex flex-wrap gap-1">
              @for (label of job.labels; track label) {
                <p-tag [value]="label" severity="info" />
              }
            </div>
          </td>
          <td class="text-right">{{ formatSeconds(job.waitSeconds) }}</td>
          <td class="text-right">{{ formatSeconds(job.etaSeconds) }}</td>
          <td><app-queued-reason-chip [reason]="job.queuedReason" /></td>
        </tr>
      </ng-template>
      <ng-template pTemplate="emptymessage">
        <tr>
          <td colspan="6" class="text-center text-surface-500">No queued jobs.</td>
        </tr>
      </ng-template>
    </p-table>
  `,
})
export class QueuedJobsTableComponent {
  jobs = input.required<QueuedJob[]>();

  formatSeconds(seconds: number | null | undefined): string {
    if (seconds == null) return '—';
    if (seconds < 60) return `${Math.round(seconds)}s`;
    if (seconds < 3600) return `${Math.round(seconds / 60)}m`;
    return `${(seconds / 3600).toFixed(1)}h`;
  }
}
