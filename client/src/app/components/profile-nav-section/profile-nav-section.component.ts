import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { IconsModule } from 'icons.module';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { ChipModule } from 'primeng/chip';
import { DataViewModule } from 'primeng/dataview';
import { TagModule } from 'primeng/tag';
import { ToastModule } from 'primeng/toast';
import { TooltipModule } from 'primeng/tooltip';
import { DividerModule } from 'primeng/divider';
import { AvatarModule } from 'primeng/avatar';

@Component({
  selector: 'app-profile-nav-section',
  imports: [ToastModule, TooltipModule, DividerModule, AvatarModule, DataViewModule, ButtonModule, TagModule, CommonModule, CardModule, ChipModule, IconsModule],
  templateUrl: './profile-nav-section.component.html',
})
export class ProfileNavSectionComponent {
  constructor(private keycloakService: KeycloakService) {}

  isLoggedIn() {
    return this.keycloakService.loggedIn();
  }

  logout() {
    this.keycloakService.logout();
  }

  initials() {
    if (!this.isLoggedIn()) {
      return '';
    }

    const profile = this.keycloakService.profile;

    return `${profile?.firstName?.charAt(0) ?? ''}${profile?.lastName?.charAt(0) ?? ''}`;
  }

  fullName() {
    if (!this.isLoggedIn()) {
      return '';
    }

    const profile = this.keycloakService.profile;

    return `${profile?.firstName} ${profile?.lastName}`;
  }

  login() {
    this.keycloakService.login();
  }
}
