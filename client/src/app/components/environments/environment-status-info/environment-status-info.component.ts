import { Component, computed, inject, input } from '@angular/core';
import { EnvironmentStatusDto } from '@app/core/modules/openapi';
import { DateService } from '@app/core/services/date.service';
import { TimeAgoPipe } from '@app/pipes/time-ago.pipe';

@Component({
  selector: 'app-environment-status-info',
  imports: [TimeAgoPipe],
  templateUrl: './environment-status-info.component.html',
})
export class EnvironmentStatusInfoComponent {
  status = input.required<EnvironmentStatusDto>();
  dateService = inject(DateService);

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
}
