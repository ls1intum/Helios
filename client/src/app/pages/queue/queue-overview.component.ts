import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { CardModule } from 'primeng/card';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { QueueDepthPanelComponent } from './components/queue-depth-panel.component';
import { QueuedJobsTableComponent } from './components/queued-jobs-table.component';
import { RunnerPoolPanelComponent } from './components/runner-pool-panel.component';
import { queueApi, runnerApi, type QueueDepth, type QueuedJob, type RunnerPool } from './queue.api';

@Component({
  selector: 'app-queue-overview',
  standalone: true,
  imports: [
    CardModule,
    ProgressSpinnerModule,
    QueueDepthPanelComponent,
    QueuedJobsTableComponent,
    RunnerPoolPanelComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="p-4 space-y-6">
      <section>
        <h2 class="mb-2 text-xl font-semibold">Queue depth</h2>
        @if (depth(); as d) {
          <app-queue-depth-panel [labelSets]="d.labelSets" />
        } @else {
          <p-progressSpinner styleClass="w-8 h-8" />
        }
      </section>

      <section>
        <h2 class="mb-2 text-xl font-semibold">Queued jobs</h2>
        <app-queued-jobs-table [jobs]="jobs()" />
      </section>

      <section>
        <h2 class="mb-2 text-xl font-semibold">Runner pools</h2>
        <app-runner-pool-panel [pools]="pools()" />
      </section>
    </div>
  `,
})
export class QueueOverviewComponent {
  private route = inject(ActivatedRoute);
  private api = queueApi();
  private rApi = runnerApi();

  repositoryId = computed(() => {
    let r = this.route.snapshot;
    while (r && !r.params['repositoryId'] && r.parent) {
      r = r.parent;
    }
    const raw = r?.params['repositoryId'];
    return raw ? Number(raw) : null;
  });

  depth = signal<QueueDepth | null>(null);
  jobs = signal<QueuedJob[]>([]);
  pools = signal<RunnerPool[]>([]);

  private interval?: ReturnType<typeof setInterval>;

  constructor() {
    effect(onCleanup => {
      const repoId = this.repositoryId();
      if (!repoId) {
        return;
      }
      const tick = async () => {
        try {
          const [d, j, p] = await Promise.all([
            this.api.depth(repoId),
            this.api.jobs(repoId, 'queued', 200),
            this.rApi.pools(),
          ]);
          this.depth.set(d);
          this.jobs.set(j);
          this.pools.set(p);
        } catch {
          // Ignore transient errors; the next tick will retry.
        }
      };
      tick();
      this.interval = setInterval(tick, 3000);
      onCleanup(() => clearInterval(this.interval));
    });
  }
}
