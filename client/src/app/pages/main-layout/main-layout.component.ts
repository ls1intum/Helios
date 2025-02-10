import { NgClass, SlicePipe } from '@angular/common';
import { Component, computed, inject, input, numberAttribute } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ProfileNavSectionComponent } from '@app/components/profile-nav-section/profile-nav-section.component';
import { getRepositoryByIdOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { PermissionService } from '@app/core/services/permission.service';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { IconsModule } from 'icons.module';
import { AvatarModule } from 'primeng/avatar';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { DividerModule } from 'primeng/divider';
import { ToastModule } from 'primeng/toast';
import { TooltipModule } from 'primeng/tooltip';
import { HeliosIconComponent } from '@app/components/helios-icon/helios-icon.component';
import { UserLockInfoComponent } from '@app/components/user-lock-info/user-lock-info.component';
import { FooterComponent } from '@app/components/footer/footer.component';
import {NavigationBarComponent} from '@app/components/navigation-bar/navigation-bar.component';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [
    RouterOutlet,
    ToastModule,
    IconsModule,
    ButtonModule,
    TooltipModule,
    DividerModule,
    AvatarModule,
    CardModule,
    NgClass,
    FooterComponent,
    NavigationBarComponent,
  ],
  templateUrl: './main-layout.component.html',
})
export class MainLayoutComponent {
  private keycloakService = inject(KeycloakService);

  repositoryId = input.required({ transform: numberAttribute });

  login() {
    this.keycloakService.login();
  }

  isLoggedIn = computed(() => this.keycloakService.isLoggedIn());
}
