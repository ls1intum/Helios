import { Component, inject, input, output } from '@angular/core';
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
import { EnvironmentDetailsComponent } from '../environment-details/environment-details.component';
import { SelectButtonModule } from 'primeng/selectbutton';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { EnvironmentStatusTagComponent } from '../environment-status-tag/environment-status-tag.component';
import { signal } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconGitPullRequest, IconHistory, IconTag } from 'angular-tabler-icons/icons';

@Component({
  selector: 'app-environment-accordion',
  imports: [
    CommonModule,
    AccordionModule,
    LockTagComponent,
    TagModule,
    UserAvatarComponent,
    TimeAgoPipe,
    ButtonModule,
    DeploymentStateTagComponent,
    TooltipModule,
    TablerIconComponent,
    EnvironmentActionsComponent,
    EnvironmentDetailsComponent,
    DeploymentStepperComponent,
    SelectButtonModule,
    FormsModule,
    RouterLink,
    EnvironmentStatusTagComponent,
  ],
  providers: [
    provideTablerIcons({
      IconTag,
      IconGitPullRequest,
      IconHistory,
    }),
  ],
  templateUrl: './environment-accordion.component.html',
})
export class EnvironmentAccordionComponent {
  readonly environment = input.required<EnvironmentDto>();
  readonly deployable = input<boolean>(false);
  readonly canViewAllEnvironments = input<boolean>(false);
  readonly timeUntilReservationExpires = input<number | undefined>(undefined);

  readonly deploy = output<EnvironmentDto>();
  readonly unlock = output<{ event: Event; environment: EnvironmentDto }>();
  readonly extend = output<{ event: Event; environment: EnvironmentDto }>();
  readonly lock = output<EnvironmentDto>();

  showLatestDeployment = signal<boolean>(true);

  private datePipe = inject(DatePipe);

  onDeploy(event: Event) {
    event.stopPropagation();
    this.deploy.emit(this.environment());
  }

  onUnlock(event: Event) {
    this.unlock.emit({ event, environment: this.environment() });
  }

  onExtend(event: Event) {
    this.extend.emit({ event, environment: this.environment() });
  }

  onLock(event: Event) {
    event.stopPropagation();
    this.lock.emit(this.environment());
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
    return (deployment.releaseCandidateNames?.length || 0) > 0 || (!!deployment.ref && /^v?\d+\.\d+\.\d+/.test(deployment.ref));
  }

  getPrLink() {
    return ['/repo', this.environment().repository?.id, 'ci-cd', 'pr', this.environment().latestDeployment?.pullRequestNumber?.toString()];
  }

  getBranchLink() {
    return ['/repo', this.environment().repository?.id, 'ci-cd', 'branch', this.environment().latestDeployment?.ref];
  }
}
