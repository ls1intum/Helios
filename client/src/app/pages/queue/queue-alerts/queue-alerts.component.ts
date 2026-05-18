import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { queueApi, type AlertEventDto, type AlertRuleDto } from '../queue.api';

@Component({
  selector: 'app-queue-alerts',
  standalone: true,
  imports: [
    ButtonModule,
    CardModule,
    FormsModule,
    InputNumberModule,
    InputTextModule,
    SelectModule,
    TableModule,
    TagModule,
    ToggleSwitchModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="p-4 space-y-6">
      <section>
        <h2 class="mb-2 text-xl font-semibold">New alert rule</h2>
        <p-card>
          <div class="grid grid-cols-1 gap-3 md:grid-cols-2">
            <div>
              <label class="block text-sm">Kind</label>
              <p-select
                [options]="kindOptions"
                [(ngModel)]="draft.kind"
                optionLabel="label"
                optionValue="value"
              />
            </div>
            <div>
              <label class="block text-sm">Threshold (seconds)</label>
              <p-inputNumber [(ngModel)]="draft.thresholdSeconds" />
            </div>
            <div>
              <label class="block text-sm">Window (minutes)</label>
              <p-inputNumber [(ngModel)]="draft.windowMinutes" />
            </div>
            <div>
              <label class="block text-sm">Quiet hours cron</label>
              <input pInputText [(ngModel)]="draft.quietHoursCron" />
            </div>
            <div class="flex items-center gap-2">
              <p-toggleSwitch [(ngModel)]="draft.enabled" />
              <span>Enabled</span>
            </div>
          </div>
          <div class="mt-3 text-right">
            <p-button label="Create rule" (onClick)="create()" />
          </div>
        </p-card>
      </section>

      <section>
        <h2 class="mb-2 text-xl font-semibold">Existing rules</h2>
        <p-table [value]="rules()">
          <ng-template pTemplate="header">
            <tr>
              <th>Kind</th>
              <th>Threshold</th>
              <th>Window</th>
              <th>Enabled</th>
              <th>Quiet</th>
              <th></th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-rule>
            <tr>
              <td>{{ rule.kind }}</td>
              <td>{{ rule.thresholdSeconds }}s</td>
              <td>{{ rule.windowMinutes }}m</td>
              <td>
                <p-tag
                  [value]="rule.enabled ? 'on' : 'off'"
                  [severity]="rule.enabled ? 'success' : 'secondary'"
                />
              </td>
              <td>{{ rule.quietHoursCron ?? '—' }}</td>
              <td><p-button label="Delete" severity="danger" (onClick)="remove(rule.id)" /></td>
            </tr>
          </ng-template>
        </p-table>
      </section>

      <section>
        <h2 class="mb-2 text-xl font-semibold">Recent events</h2>
        <p-table [value]="events()">
          <ng-template pTemplate="header">
            <tr>
              <th>Fired at</th>
              <th>Cleared at</th>
              <th>Rule</th>
              <th>Measured</th>
              <th>Details</th>
            </tr>
          </ng-template>
          <ng-template pTemplate="body" let-evt>
            <tr>
              <td>{{ evt.firedAt }}</td>
              <td>{{ evt.clearedAt ?? '—' }}</td>
              <td>{{ evt.ruleId }}</td>
              <td>{{ evt.measuredValue }}</td>
              <td>{{ evt.details }}</td>
            </tr>
          </ng-template>
        </p-table>
      </section>
    </div>
  `,
})
export class QueueAlertsComponent {
  private route = inject(ActivatedRoute);
  private api = queueApi();

  kindOptions = [
    { label: 'Queue p95 over threshold', value: 'QUEUE_P95_OVER' },
    { label: 'Runners offline over threshold', value: 'RUNNER_OFFLINE_OVER' },
    { label: 'Stuck jobs over threshold', value: 'STUCK_JOBS_OVER' },
  ];

  draft: AlertRuleDto = {
    id: null,
    kind: 'QUEUE_P95_OVER',
    thresholdSeconds: 600,
    windowMinutes: 5,
    repositoryId: null,
    labelSetHash: null,
    channels: ['EMAIL'],
    enabled: true,
    quietHoursCron: null,
  };

  rules = signal<AlertRuleDto[]>([]);
  events = signal<AlertEventDto[]>([]);

  repositoryId = computed(() => {
    let r = this.route.snapshot;
    while (r && !r.params['repositoryId'] && r.parent) {
      r = r.parent;
    }
    const raw = r?.params['repositoryId'];
    return raw ? Number(raw) : null;
  });

  constructor() {
    effect(async () => {
      const repoId = this.repositoryId();
      if (!repoId) return;
      await this.refresh();
    });
  }

  private async refresh() {
    const repoId = this.repositoryId();
    if (!repoId) return;
    try {
      const [rules, events] = await Promise.all([
        this.api.listRules(repoId),
        this.api.events(repoId, 72),
      ]);
      this.rules.set(rules);
      this.events.set(events);
    } catch {
      // Ignore.
    }
  }

  async create() {
    const repoId = this.repositoryId();
    if (!repoId) return;
    try {
      await this.api.createRule(repoId, this.draft);
      await this.refresh();
    } catch {
      // Ignore.
    }
  }

  async remove(id: number) {
    const repoId = this.repositoryId();
    if (!repoId) return;
    try {
      await this.api.deleteRule(repoId, id);
      await this.refresh();
    } catch {
      // Ignore.
    }
  }
}
