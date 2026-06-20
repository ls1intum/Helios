import { Component, inject, input, ViewChild } from '@angular/core';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { ChipModule } from 'primeng/chip';
import { DataViewModule } from 'primeng/dataview';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { DividerModule } from 'primeng/divider';
import { AvatarModule } from 'primeng/avatar';
import { Popover, PopoverModule } from 'primeng/popover';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconBrandGithub, IconChecklist, IconLogout, IconSettings } from 'angular-tabler-icons/icons';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { myPendingApprovalsOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { computed } from '@angular/core';
import { Badge } from 'primeng/badge';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { filter } from 'rxjs';

@Component({
  selector: 'app-profile-nav-section',
  imports: [ToastModule, PopoverModule, DividerModule, AvatarModule, DataViewModule, ButtonModule, TagModule, CardModule, ChipModule, TablerIconComponent, RouterLink, Badge],
  providers: [
    provideTablerIcons({
      IconBrandGithub,
      IconChecklist,
      IconLogout,
      IconSettings,
    }),
  ],
  templateUrl: './profile-nav-section.component.html',
})
export class ProfileNavSectionComponent {
  @ViewChild('profileMenu') profileMenu?: Popover;

  private keycloakService = inject(KeycloakService);
  private router = inject(Router);

  isExpanded = input.required<boolean>();

  /** Drives the badge on the profile button. Polls every 30 s; cheap server-side query. */
  pendingApprovalsQuery = injectQuery(() => ({
    ...myPendingApprovalsOptions(),
    enabled: () => this.isLoggedIn(),
    refetchInterval: 30_000,
    refetchOnWindowFocus: true,
  }));

  pendingApprovalsCount = computed(() => this.pendingApprovalsQuery.data()?.length ?? 0);

  constructor() {
    this.router.events.pipe(filter(event => event instanceof NavigationEnd)).subscribe(() => {
      this.profileMenu?.hide?.();
    });
  }

  isLoggedIn() {
    return this.keycloakService.isLoggedIn();
  }

  logout() {
    this.keycloakService.logout();
  }

  fullName() {
    if (!this.isLoggedIn()) {
      return '';
    }

    const profile = this.keycloakService.profile;

    return `${profile?.firstName} ${profile?.lastName}`;
  }

  getProfilePictureUrl() {
    return this.keycloakService.getUserGithubProfilePictureUrl();
  }

  getProfileUrl() {
    return this.keycloakService.getUserGithubProfileUrl();
  }

  login() {
    this.keycloakService.login();
  }

  openProfile() {
    window.open(this.getProfileUrl());
  }
}
