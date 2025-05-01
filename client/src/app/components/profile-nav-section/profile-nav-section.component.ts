import { CommonModule } from '@angular/common';
import {Component, inject, input, ViewChild} from '@angular/core';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { ChipModule } from 'primeng/chip';
import { DataViewModule } from 'primeng/dataview';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { DividerModule } from 'primeng/divider';
import { AvatarModule } from 'primeng/avatar';
import {Popover, PopoverModule} from 'primeng/popover';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconLogin, IconLogout, IconSettings } from 'angular-tabler-icons/icons';
import {NavigationEnd, Router, RouterLink} from '@angular/router';
import {filter} from 'rxjs';

@Component({
  selector: 'app-profile-nav-section',
  imports: [
    ToastModule,
    PopoverModule,
    DividerModule,
    AvatarModule,
    DataViewModule,
    ButtonModule,
    TagModule,
    CommonModule,
    CardModule,
    ChipModule,
    TablerIconComponent,
    RouterLink,
  ],
  providers: [
    provideTablerIcons({
      IconLogin,
      IconLogout,
      IconSettings,
    }),
  ],
  templateUrl: './profile-nav-section.component.html',
})
export class ProfileNavSectionComponent {
  @ViewChild('profileMenu') profileMenu?: Popover;

  private keycloakService = inject(KeycloakService);

  isExpanded = input.required<boolean>();

  constructor(private router: Router) {
    this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe(() => {
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
