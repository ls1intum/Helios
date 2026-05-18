import { ChangeDetectionStrategy, Component, effect, signal } from '@angular/core';
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { runnerApi, type RunnerDto } from '../queue.api';

@Component({
  selector: 'app-runner-list',
  standalone: true,
  imports: [CardModule, TableModule, TagModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="p-4">
      <h2 class="mb-2 text-xl font-semibold">Self-hosted runners</h2>
      <p-table [value]="runners()" [paginator]="true" [rows]="20">
        <ng-template pTemplate="header">
          <tr>
            <th>Name</th>
            <th>OS</th>
            <th>Group</th>
            <th>Status</th>
            <th>Busy</th>
            <th>Labels</th>
            <th>Current job</th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-r>
          <tr>
            <td>{{ r.name }}</td>
            <td>{{ r.os }}</td>
            <td>{{ r.runnerGroupName }}</td>
            <td>
              <p-tag
                [value]="r.status"
                [severity]="r.status === 'ONLINE' ? 'success' : 'danger'"
              />
            </td>
            <td>
              @if (r.busy) {
                <p-tag value="busy" severity="warn" />
              } @else if (r.status === 'ONLINE') {
                <p-tag value="idle" severity="info" />
              } @else {
                <span class="text-surface-400">—</span>
              }
            </td>
            <td>
              <div class="flex flex-wrap gap-1">
                @for (label of r.labels; track label) {
                  <p-tag [value]="label" severity="secondary" />
                }
              </div>
            </td>
            <td>
              @if (r.currentJobId) {
                <a [href]="'#'">job #{{ r.currentJobId }}</a>
              } @else {
                <span class="text-surface-400">—</span>
              }
            </td>
          </tr>
        </ng-template>
      </p-table>
    </div>
  `,
})
export class RunnerListComponent {
  private api = runnerApi();
  runners = signal<RunnerDto[]>([]);
  private interval?: ReturnType<typeof setInterval>;

  constructor() {
    effect(onCleanup => {
      const tick = async () => {
        try {
          this.runners.set(await this.api.list());
        } catch {
          // Ignore; next tick retries.
        }
      };
      tick();
      this.interval = setInterval(tick, 5000);
      onCleanup(() => clearInterval(this.interval));
    });
  }
}
