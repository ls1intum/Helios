import { CommonModule } from '@angular/common';
import { Component, Input } from '@angular/core';
import { EnvironmentDto } from '@app/core/modules/openapi';
import { EnvironmentDeploymentInfoComponent } from '../deployment-info/environment-deployment-info.component';
import { EnvironmentStatusInfoComponent } from '../environment-status-info/environment-status-info.component';

@Component({
  selector: 'app-environment-details',
  imports: [CommonModule, EnvironmentDeploymentInfoComponent, EnvironmentStatusInfoComponent],
  templateUrl: './environment-details.component.html',
})
export class EnvironmentDetailsComponent {
  @Input() environment!: EnvironmentDto;
}
