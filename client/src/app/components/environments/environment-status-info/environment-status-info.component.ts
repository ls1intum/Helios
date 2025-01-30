import { Component, input } from '@angular/core';
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
}
