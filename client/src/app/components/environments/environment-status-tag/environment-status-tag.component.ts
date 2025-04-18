import { Component, input } from '@angular/core';
import { EnvironmentStatusDto } from '@app/core/modules/openapi';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconCheck, IconExclamationCircle } from 'angular-tabler-icons/icons';
import { TagModule } from 'primeng/tag';

@Component({
  selector: 'app-environment-status-tag',
  imports: [TagModule, TablerIconComponent],
  providers: [
    provideTablerIcons({
      IconCheck,
      IconExclamationCircle,
    }),
  ],
  templateUrl: './environment-status-tag.component.html',
})
export class EnvironmentStatusTagComponent {
  status = input.required<EnvironmentStatusDto>();
}
