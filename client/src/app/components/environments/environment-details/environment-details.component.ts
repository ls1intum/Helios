import { Component, input } from '@angular/core';
import { EnvironmentDto } from '@app/core/modules/openapi';
import { EnvironmentDeploymentInfoComponent } from '../deployment-info/environment-deployment-info.component';
import { EnvironmentStatusInfoComponent } from '../environment-status-info/environment-status-info.component';

@Component({
  selector: 'app-environment-details',
  imports: [EnvironmentDeploymentInfoComponent, EnvironmentStatusInfoComponent],
  templateUrl: './environment-details.component.html',
})
export class EnvironmentDetailsComponent {
  readonly environment = input.required<EnvironmentDto>();
}
