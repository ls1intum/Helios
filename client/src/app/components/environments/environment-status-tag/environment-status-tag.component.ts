import { Component, input } from '@angular/core';
import { EnvironmentStatusDto } from '@app/core/modules/openapi';
import { IconsModule } from 'icons.module';
import { TagModule } from 'primeng/tag';

@Component({
  selector: 'app-environment-status-tag',
  imports: [TagModule, IconsModule],
  templateUrl: './environment-status-tag.component.html',
})
export class EnvironmentStatusTagComponent {
  status = input.required<EnvironmentStatusDto>();
}
