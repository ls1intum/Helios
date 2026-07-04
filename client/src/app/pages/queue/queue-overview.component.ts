import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
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
  imports: [CardModule, ProgressSpinnerModule, QueueDepthPanelComponent, QueuedJobsTableComponent, RunnerPoolPanelComponent],
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

  // Reactive — re-fires when the URL's repositoryId param changes (e.g. switching repos in-place).
  private paramMap = toSignal(this.route.paramMap, { requireSync: true });
  repositoryId = computed(() => {
    // Walk up the active route chain via paramMap snapshots — the route param can live on a
    // parent (e.g. /repo/:repositoryId/ci-cd/queue).
    let r: ActivatedRoute | null = this.route;
    // Touch the reactive paramMap so the computed re-evaluates on navigation.
    this.paramMap();
    while (r) {
      const raw = r.snapshot.paramMap.get('repositoryId');
      if (raw && !isNaN(Number(raw))) {
        return Number(raw);
      }
      r = r.parent;
    }
    return null;
  });

  depth = signal<QueueDepth | null>(null);
  jobs = signal<QueuedJob[]>([]);
  pools = signal<RunnerPool[]>([]);

  private interval?: ReturnType<typeof setInterval>;

  constructor() {
    effect(onCleanup => {
      const repoId = this.repositoryId();
      const tick = async () => {
        try {
          if (repoId) {
            const [d, j, p] = await Promise.all([this.api.depth(repoId), this.api.jobs(repoId, 'queued', 200), this.rApi.pools()]);
            this.depth.set(d);
            this.jobs.set(j);
            this.pools.set(p);
          } else {
            // Admin /queue route — no repositoryId, fall back to the org-wide endpoint.
            const [d, p] = await Promise.all([this.api.orgDepth(), this.rApi.pools()]);
            this.depth.set(d);
            this.jobs.set([]); // org-wide queued-job listing isn't exposed yet
            this.pools.set(p);
          }
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
