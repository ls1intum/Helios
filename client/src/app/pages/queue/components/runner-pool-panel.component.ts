import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { CardModule } from 'primeng/card';
import { TagModule } from 'primeng/tag';
import type { RunnerPool } from '../queue.api';

@Component({
  selector: 'app-runner-pool-panel',
  standalone: true,
  imports: [CardModule, TagModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="grid grid-cols-1 gap-3 md:grid-cols-2 lg:grid-cols-3">
      @for (pool of pools(); track pool.labels.join(',')) {
        <p-card>
          <div class="flex flex-wrap gap-1">
            @for (label of pool.labels; track label) {
              <p-tag [value]="label" severity="contrast" />
            }
            @if (!pool.labels.length) {
              <p-tag value="(no labels)" severity="secondary" />
            }
          </div>
          <div class="mt-3 grid grid-cols-4 gap-3 text-center">
            <div>
              <div class="text-xl font-semibold text-green-500">{{ pool.busy }}</div>
              <div class="text-xs text-surface-500">busy</div>
            </div>
            <div>
              <div class="text-xl font-semibold">{{ pool.idle }}</div>
              <div class="text-xs text-surface-500">idle</div>
            </div>
            <div>
              <div class="text-xl font-semibold text-red-500">{{ pool.offline }}</div>
              <div class="text-xs text-surface-500">offline</div>
            </div>
            <div>
              <div class="text-xl font-semibold">{{ pool.online }}</div>
              <div class="text-xs text-surface-500">online&nbsp;total</div>
            </div>
          </div>
        </p-card>
      }
      @if (!pools().length) {
        <div class="text-surface-500">No runner pools.</div>
      }
    </div>
  `,
})
export class RunnerPoolPanelComponent {
  pools = input.required<RunnerPool[]>();
}
