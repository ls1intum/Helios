import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TagModule } from 'primeng/tag';

@Component({
  selector: 'app-queued-reason-chip',
  standalone: true,
  imports: [TagModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (reason()) {
      <p-tag [value]="label()" [severity]="severity()" />
    } @else {
      <span class="text-surface-400">—</span>
    }
  `,
})
export class QueuedReasonChipComponent {
  reason = input<string | null>(null);

  label = computed<string>(() => {
    switch (this.reason()) {
      case 'PENDING_APPROVAL':
        return 'pending approval';
      case 'NO_RUNNER_ONLINE':
        return 'no runner online';
      case 'RUNNERS_BUSY':
        return 'runners busy';
      case 'CONCURRENCY_LOCK':
        return 'likely concurrency lock';
      case 'UNKNOWN':
        return 'unknown';
      default:
        return this.reason() ?? '';
    }
  });

  severity = computed<'danger' | 'warn' | 'info' | 'secondary'>(() => {
    switch (this.reason()) {
      case 'NO_RUNNER_ONLINE':
        return 'danger';
      case 'RUNNERS_BUSY':
      case 'CONCURRENCY_LOCK':
        return 'warn';
      case 'PENDING_APPROVAL':
        return 'info';
      default:
        return 'secondary';
    }
  });
}
