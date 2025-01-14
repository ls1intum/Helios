import { Component, Input, OnInit, OnDestroy, signal } from '@angular/core';
import { TagModule } from 'primeng/tag';

@Component({
  selector: 'app-lock-time',
  imports: [TagModule],
  templateUrl: './lock-time.component.html',
})
export class LockTimeComponent implements OnInit, OnDestroy {
  @Input() lockedAt?: string;

  // track the current time in a signal that we update every second
  timeNow = signal<Date>(new Date());

  // store the interval ID so we can clear it later
  private intervalId?: ReturnType<typeof setInterval>;

  ngOnInit() {
    // Update timeNow every second
    this.intervalId = setInterval(() => {
      this.timeNow.set(new Date());
    }, 1000);
  }

  ngOnDestroy() {
    // Prevent memory leaks by clearing the interval
    if (this.intervalId) {
      clearInterval(this.intervalId);
    }
  }

  timeSinceLocked(): string {
    if (!this.lockedAt) {
      return '';
    }

    const lockedDate = new Date(this.lockedAt);
    const now = this.timeNow();
    const diffMs = now.getTime() - lockedDate.getTime();

    // If the locked time is in the future, return empty
    if (diffMs < 0) {
      return '';
    }

    const totalSeconds = Math.floor(diffMs / 1000);
    const days = Math.floor(totalSeconds / 86400);
    const hours = Math.floor((totalSeconds % 86400) / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;

    // Build a list of non-zero time parts
    const parts: string[] = [];
    if (days > 0) {
      parts.push(`${days} day${days > 1 ? 's' : ''}`);
    }
    if (hours > 0) {
      parts.push(`${hours} hour${hours > 1 ? 's' : ''}`);
    }
    if (minutes > 0) {
      parts.push(`${minutes} minute${minutes > 1 ? 's' : ''}`);
    }
    // Always show seconds
    parts.push(`${seconds} second${seconds !== 1 ? 's' : ''}`);

    // Join them with a comma and a space: e.g., "2 hours, 8 minutes, 23 seconds ago"
    return parts.join(', ') + ' ago';
  }
}
