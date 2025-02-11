import { Component, computed, inject, input, OnDestroy, OnInit, signal } from '@angular/core';
import { EnvironmentStatusDto } from '@app/core/modules/openapi';
import { DateService } from '@app/core/services/date.service';
import { TimeAgoPipe } from '@app/pipes/time-ago.pipe';

@Component({
  selector: 'app-environment-status-info',
  providers: [TimeAgoPipe],
  templateUrl: './environment-status-info.component.html',
})
export class EnvironmentStatusInfoComponent implements OnInit, OnDestroy {
  timeAgoPipe = inject(TimeAgoPipe);

  status = input.required<EnvironmentStatusDto>();
  dateService = inject(DateService);
  checkedAt = computed(() => this.status().checkedAt);

  // Using it as a pipe won't update the value
  timeSinceChecked = computed(() => {
    return this.timeAgoPipe.transform(this.checkedAt(), {
      showSeconds: true,
      referenceDate: this.timeNow(),
    });
  });

  // track the current time in a signal that we update every second
  timeNow = signal<Date>(new Date());

  // store the interval ID so we can clear it later
  private intervalId?: ReturnType<typeof setInterval>;

  artemisBuildInfo = computed<{ label: string; value?: string }[]>(() => {
    const status = this.status();
    const metadata = status.metadata as
      | {
          name?: string;
          group?: string;
          version?: string;
          buildTime?: number;
        }
      | undefined;

    return [
      {
        label: 'Name',
        value: metadata?.name,
      },
      {
        label: 'Group',
        value: metadata?.group,
      },
      {
        label: 'Version',
        value: metadata?.version,
      },
      {
        label: 'Build Time',
        value: metadata?.buildTime ? this.dateService.formatDate(metadata.buildTime * 1000, 'yyyy-MM-dd HH:mm:ss') || '-/-' : undefined,
      },
    ];
  });

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
