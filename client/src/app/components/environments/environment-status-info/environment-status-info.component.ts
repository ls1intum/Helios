import { Component, computed, inject, input } from '@angular/core';
import { EnvironmentStatusDto } from '@app/core/modules/openapi';
import { DatePipe } from '@angular/common';
import { TimeAgoPipe } from '@app/pipes/time-ago.pipe';

@Component({
  selector: 'app-environment-status-info',
  imports: [TimeAgoPipe],
  providers: [DatePipe],
  templateUrl: './environment-status-info.component.html',
})
export class EnvironmentStatusInfoComponent {
  status = input.required<EnvironmentStatusDto>();
  datePipe = inject(DatePipe);

  artemisBuildInfo = computed(() => {
    const status = this.status();
    const metadata = status.metadata as {
      name?: string;
      group?: string;
      version?: string;
      buildTime?: number;
    };

    return {
      name: metadata.name,
      group: metadata.group,
      version: metadata.version,
      buildTime: metadata.buildTime ? this.datePipe.transform(metadata.buildTime * 1000, 'yyyy-MM-dd HH:mm:ss') : null,
    };
  });
}
