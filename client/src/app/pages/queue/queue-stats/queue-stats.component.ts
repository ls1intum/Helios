import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { CardModule } from 'primeng/card';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { HeliosLineChartComponent, type ChartSeries } from '@app/components/charts/helios-line-chart.component';
import { queueApi, type QueueStats } from '../queue.api';

@Component({
  selector: 'app-queue-stats',
  standalone: true,
  imports: [CardModule, FormsModule, InputTextModule, SelectModule, HeliosLineChartComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="p-4 space-y-4">
      <h2 class="text-xl font-semibold">Queue statistics</h2>

      <div class="flex flex-wrap items-center gap-2">
        <input pInputText placeholder="Workflow" [ngModel]="workflowFilter()" (ngModelChange)="workflowFilter.set($event)" />
        <input pInputText placeholder="Job" [ngModel]="jobFilter()" (ngModelChange)="jobFilter.set($event)" />
        <input pInputText placeholder="Branch" [ngModel]="branchFilter()" (ngModelChange)="branchFilter.set($event)" />
        <p-select [options]="windowOptions" [ngModel]="windowSel()" (ngModelChange)="windowSel.set($event)" optionLabel="label" optionValue="value" />
      </div>

      @if (stats(); as s) {
        <div class="grid grid-cols-2 gap-4 md:grid-cols-6">
          <p-card
            ><strong>{{ s.samples }}</strong>
            <div class="text-xs">samples</div></p-card
          >
          <p-card
            ><strong>{{ s.queueP50 ?? '—' }}</strong>
            <div class="text-xs">queue&nbsp;p50</div></p-card
          >
          <p-card
            ><strong>{{ s.queueP95 ?? '—' }}</strong>
            <div class="text-xs">queue&nbsp;p95</div></p-card
          >
          <p-card
            ><strong>{{ s.runP50 ?? '—' }}</strong>
            <div class="text-xs">run&nbsp;p50</div></p-card
          >
          <p-card
            ><strong>{{ s.runP95 ?? '—' }}</strong>
            <div class="text-xs">run&nbsp;p95</div></p-card
          >
        </div>

        <app-helios-line-chart [series]="series()" yAxisLabel="seconds" />
      }
    </div>
  `,
})
export class QueueStatsComponent {
  private route = inject(ActivatedRoute);
  private api = queueApi();

  windowOptions = [
    { label: 'Last 7 days', value: '7d' },
    { label: 'Last 30 days', value: '30d' },
  ];

  workflowFilter = signal<string>('');
  jobFilter = signal<string>('');
  branchFilter = signal<string>('');
  windowSel = signal<'7d' | '30d'>('7d');

  stats = signal<QueueStats | null>(null);
  private interval?: ReturnType<typeof setInterval>;

  private paramMap = toSignal(this.route.paramMap, { requireSync: true });
  repositoryId = computed(() => {
    this.paramMap();
    let r: ActivatedRoute | null = this.route;
    while (r) {
      const raw = r.snapshot.paramMap.get('repositoryId');
      if (raw && !isNaN(Number(raw))) {
        return Number(raw);
      }
      r = r.parent;
    }
    return null;
  });

  series = computed<ChartSeries[]>(() => {
    const s = this.stats();
    if (!s) return [];
    return [
      {
        label: 'queue p50',
        data: s.trend.map(t => ({ x: t.bucket, y: t.queueP50 ?? 0 })),
      },
      {
        label: 'run p50',
        data: s.trend.map(t => ({ x: t.bucket, y: t.runP50 ?? 0 })),
      },
    ];
  });

  constructor() {
    // Effect re-runs when any filter signal changes, immediately re-fetching with new params.
    effect(onCleanup => {
      const repoId = this.repositoryId();
      if (!repoId) return;
      const workflow = this.workflowFilter();
      const job = this.jobFilter();
      const branch = this.branchFilter();
      const window = this.windowSel();
      const tick = async () => {
        try {
          this.stats.set(
            await this.api.stats(repoId, {
              workflow: workflow || undefined,
              job: job || undefined,
              branch: branch || undefined,
              window,
            })
          );
        } catch {
          // Ignore; next tick retries.
        }
      };
      tick();
      this.interval = setInterval(tick, 30_000);
      onCleanup(() => clearInterval(this.interval));
    });
  }
}
