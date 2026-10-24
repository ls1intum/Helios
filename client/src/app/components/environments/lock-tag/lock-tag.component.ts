import { Component, computed, input, ChangeDetectionStrategy } from '@angular/core';
import { TagModule } from 'primeng/tag';

@Component({
  selector: 'app-lock-tag',
  imports: [TagModule],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './lock-tag.component.html',
})
export class LockTagComponent {
  isLocked = input.required<boolean>();

  title = computed(() => (this.isLocked() ? 'Locked' : 'Available'));
  severity = computed(() => (this.isLocked() ? 'danger' : 'success'));
}
