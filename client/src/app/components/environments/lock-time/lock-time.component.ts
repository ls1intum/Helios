import { Component, input, OnInit, OnDestroy, signal, inject, computed } from '@angular/core';
import { TagModule } from 'primeng/tag';
import { DateService } from '@app/core/services/date.service';
import { TooltipModule } from 'primeng/tooltip';

@Component({
  selector: 'app-lock-time',
  imports: [TagModule, TooltipModule],
  templateUrl: './lock-time.component.html',
})
export class LockTimeComponent implements OnInit, OnDestroy {
  timeLockWillExpire = input.required<string | undefined>();
  private dateService = inject(DateService);

  timeUntilLockExpires = computed(() => {
    const timeLockWillExpireValue = this.timeLockWillExpire();
    if (!timeLockWillExpireValue || timeLockWillExpireValue === undefined || timeLockWillExpireValue === null || timeLockWillExpireValue === '') {
      return 'Unlimited';
    }
    return this.dateService.timeUntilExpire(this.timeNow(), this.timeLockWillExpire());
  });

  autoReleaseToolTip = computed(() => {
    if (this.timeUntilLockExpires() === 'Unlimited') {
      return 'This environment is locked indefinitely';
    } else {
      return `This environment will be automatically unlocked in`;
    }
  });
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
