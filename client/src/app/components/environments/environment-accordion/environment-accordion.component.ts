import { Component, EventEmitter, inject, Input, Output } from '@angular/core';
import { EnvironmentDeployment, EnvironmentDto } from '@app/core/modules/openapi';
import { DeploymentStepperComponent } from '../deployment-stepper/deployment-stepper.component';
import { EnvironmentActionsComponent } from '../environment-actions/environment-actions.component';
import { DeploymentStateTagComponent } from '../deployment-state-tag/deployment-state-tag.component';
import { UserAvatarComponent } from '@app/components/user-avatar/user-avatar.component';
import { LockTagComponent } from '../lock-tag/lock-tag.component';
import { AccordionModule } from 'primeng/accordion';
import { TagModule } from 'primeng/tag';
import { CommonModule, DatePipe } from '@angular/common';
import { TimeAgoPipe } from '@app/pipes/time-ago.pipe';
import { TooltipModule } from 'primeng/tooltip';
import { IconsModule } from 'icons.module';
import { EnvironmentDetailsComponent } from '../environment-details/environment-details.component';
import { SelectButtonModule } from 'primeng/selectbutton';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { EnvironmentStatusTagComponent } from '../environment-status-tag/environment-status-tag.component';

@Component({
  selector: 'app-environment-accordion',
  imports: [
    CommonModule,
    AccordionModule,
    LockTagComponent,
    TagModule,
    UserAvatarComponent,
    TimeAgoPipe,
    DeploymentStateTagComponent,
    TooltipModule,
    IconsModule,
    EnvironmentActionsComponent,
    EnvironmentDetailsComponent,
    DeploymentStepperComponent,
    SelectButtonModule,
    FormsModule,
    RouterLink,
    EnvironmentStatusTagComponent,
  ],
  templateUrl: './environment-accordion.component.html',
})
export class EnvironmentAccordionComponent {
  @Input() environment!: EnvironmentDto;
  @Input() deployable: boolean = false;
  @Input() canViewAllEnvironments: boolean = false;
  @Input() timeUntilReservationExpires: number | undefined;

  @Output() deploy = new EventEmitter<EnvironmentDto>();
  @Output() unlock = new EventEmitter<{ event: Event; environment: EnvironmentDto }>();
  @Output() extend = new EventEmitter<{ event: Event; environment: EnvironmentDto }>();
  @Output() lock = new EventEmitter<EnvironmentDto>();

  showLatestDeployment: boolean = true;

  private datePipe = inject(DatePipe);

  onDeploy(event: Event) {
    event.stopPropagation();
    this.deploy.emit(this.environment);
  }

  onUnlock(event: Event) {
    this.unlock.emit({ event, environment: this.environment });
  }

  onExtend(event: Event) {
    this.extend.emit({ event, environment: this.environment });
  }

  onLock(event: Event) {
    event.stopPropagation();
    this.lock.emit(this.environment);
  }

  formatEnvironmentType(type: string): string {
    return type.charAt(0).toUpperCase() + type.slice(1).toLowerCase();
  }

  getDeploymentTime(environment: EnvironmentDto) {
    const date = environment.latestDeployment?.updatedAt;
    return date ? this.datePipe.transform(date, 'd MMMM y, h:mm a') : null;
  }

  openExternalLink(event: MouseEvent, link?: string): void {
    event.stopPropagation();
    if (link) {
      window.open(this.getFullUrl(link), '_blank');
    }
  }

  getFullUrl(url: string): string {
    if (url && !url.startsWith('http') && !url.startsWith('https')) {
      return 'http://' + url;
    }
    return url;
  }

  isRelease(deployment: EnvironmentDeployment): boolean {
    return !!deployment.releaseCandidateName || (!!deployment.ref && /^v?\d+\.\d+\.\d+/.test(deployment.ref));
  }

  getPrLink() {
    return ['/repo', this.environment.repository?.id, 'ci-cd', 'pr', this.environment.latestDeployment?.pullRequestNumber?.toString()];
  }

  getBranchLink() {
    return ['/repo', this.environment.repository?.id, 'ci-cd', 'branch', this.environment.latestDeployment?.ref];
  }
}
