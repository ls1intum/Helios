import { Component, computed, inject, input, numberAttribute } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { PrimeTemplate } from 'primeng/api';
import { SkeletonModule } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { IconsModule } from 'icons.module';
import { getActivityHistoryByEnvironmentIdOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { DateService } from '@app/core/services/date.service';
import { CommonModule } from '@angular/common';
import { PageHeadingComponent } from '@app/components/page-heading/page-heading.component';
import { AvatarModule } from 'primeng/avatar';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { TooltipModule } from 'primeng/tooltip';

@Component({
  selector: 'app-environment-deployment-history',
  imports: [CommonModule, IconsModule, PrimeTemplate, SkeletonModule, TableModule, PageHeadingComponent, AvatarModule, TooltipModule],
  templateUrl: './environment-deployment-history.component.html',
})
export class EnvironmentDeploymentHistoryComponent {
  dateService = inject(DateService);
  keycloakService = inject(KeycloakService);

  environmentId = input.required({ transform: numberAttribute });
  repositoryId = input.required({ transform: numberAttribute });

  activityHistoryQuery = injectQuery(() => getActivityHistoryByEnvironmentIdOptions({ path: { environmentId: this.environmentId() } }));
  activityHistory = computed(() => this.activityHistoryQuery.data());

  getAvatarBorderClass(login: string) {
    return this.keycloakService.isCurrentUser(login) ? 'border-2 border-primary-400 rounded-full' : '';
  }

  openUserProfile(login: string) {
    //Redirect to the user's github profile
    window.open(`
      https://www.github.com/${login}
    `);
  }
}
