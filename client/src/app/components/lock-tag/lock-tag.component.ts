import { Component, computed, Input } from '@angular/core';
import { TagModule } from 'primeng/tag';

@Component({
  selector: 'app-lock-tag',
  imports: [TagModule],
  templateUrl: './lock-tag.component.html',
  styleUrl: './lock-tag.component.css',
})
export class LockTagComponent {
  @Input({ required: true }) isLocked!: boolean;

  title = computed(() => (this.isLocked ? 'Locked' : 'Free'));
  severity = computed(() => (this.isLocked ? 'danger' : 'success'));
}
