import { CommonModule } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { TooltipModule } from 'primeng/tooltip';
import { AvatarModule } from 'primeng/avatar';
import { TagModule } from 'primeng/tag';
import { getEnvironmentReviewersOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { Reviewer } from '@app/core/modules/openapi';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconExclamationMark, IconShieldExclamation, IconUser, IconUsersGroup } from 'angular-tabler-icons/icons';

@Component({
  selector: 'app-environment-reviewers',
  standalone: true,
  imports: [CommonModule, TablerIconComponent, TooltipModule, AvatarModule, TagModule],
  providers: [
    provideTablerIcons({
      IconShieldExclamation,
      IconUsersGroup,
      IconUser,
      IconExclamationMark,
    }),
  ],
  templateUrl: './environment-reviewers.component.html',
})
export class EnvironmentReviewersComponent {
  keycloakService = inject(KeycloakService);
  readonly environmentId = input.required<number>();

  reviewersQuery = injectQuery(() => ({
    ...getEnvironmentReviewersOptions({ path: { environmentId: this.environmentId() }, throwOnError: false }),
    enabled: !!this.environmentId(),
  }));

  login = this.keycloakService.getPreferredUsername();
  reviewers = computed(() => this.reviewersQuery.data()?.reviewers ?? []);
  hasReviewers = computed(() => this.reviewers().length > 0);
  preventSelfReview = computed(() => this.reviewersQuery.data()?.preventSelfReview ?? false);

  // Helper to check if a reviewer is a team based on having a name property
  isTeam = (reviewer: Reviewer) => !!reviewer.name;
}
