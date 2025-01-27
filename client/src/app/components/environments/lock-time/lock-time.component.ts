import { Component, Input, OnInit, OnDestroy, signal, inject } from '@angular/core';
import { TagModule } from 'primeng/tag';
import { DateService } from '@app/core/services/date.service';

@Component({
  selector: 'app-lock-time',
  imports: [TagModule],
  templateUrl: './lock-time.component.html',
})
export class LockTimeComponent implements OnInit, OnDestroy {
  @Input() lockedAt?: string;
  dateService = inject(DateService);

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
}
