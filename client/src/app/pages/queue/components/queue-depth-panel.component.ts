import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { CardModule } from 'primeng/card';
import { TagModule } from 'primeng/tag';
import type { LabelSetDepth } from '../queue.api';

@Component({
  selector: 'app-queue-depth-panel',
  standalone: true,
  imports: [CardModule, TagModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="grid grid-cols-1 gap-3 md:grid-cols-2 lg:grid-cols-3">
      @for (set of labelSets(); track set.labels.join(',')) {
        <p-card>
          <div class="flex items-start justify-between gap-2">
            <div class="flex flex-wrap gap-1">
              @for (label of set.labels; track label) {
                <p-tag [value]="label" severity="info" />
              }
              @if (!set.labels.length) {
                <p-tag value="(no labels)" severity="secondary" />
              }
            </div>
            @if (set.runnerKind) {
              <p-tag [value]="set.runnerKind" severity="contrast" />
            }
          </div>
          <div class="mt-3 grid grid-cols-3 gap-3 text-center">
            <div>
              <div class="text-2xl font-semibold">{{ set.queued }}</div>
              <div class="text-xs text-surface-500">queued</div>
            </div>
            <div>
              <div class="text-2xl font-semibold">{{ set.inProgress }}</div>
              <div class="text-xs text-surface-500">in&nbsp;progress</div>
            </div>
            <div>
              <div class="text-2xl font-semibold">{{ formatSeconds(set.oldestQueuedSeconds) }}</div>
              <div class="text-xs text-surface-500">oldest&nbsp;wait</div>
            </div>
          </div>
        </p-card>
      }
      @if (!labelSets().length) {
        <div class="text-surface-500">No active jobs.</div>
      }
    </div>
  `,
})
export class QueueDepthPanelComponent {
  labelSets = input.required<LabelSetDepth[]>();

  formatSeconds(seconds: number | null | undefined): string {
    if (seconds == null) return '—';
    if (seconds < 60) return `${Math.round(seconds)}s`;
    if (seconds < 3600) return `${Math.round(seconds / 60)}m`;
    return `${(seconds / 3600).toFixed(1)}h`;
  }
}
