import { Component, OnInit, inject, input, numberAttribute } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { IconsModule } from 'icons.module';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { HeliosIconComponent } from '../../components/helios-icon/helios-icon.component';
import { DividerModule } from 'primeng/divider';
import { AvatarModule } from 'primeng/avatar';
import { ToastModule } from 'primeng/toast';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { CardModule } from 'primeng/card';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { getRepositoryByIdOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { SlicePipe } from '@angular/common';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    SlicePipe,
    ToastModule,
    RouterLinkActive,
    IconsModule,
    ButtonModule,
    TooltipModule,
    HeliosIconComponent,
    DividerModule,
    AvatarModule,
    CardModule,
  ],
  templateUrl: './main-layout.component.html',
})
export class MainLayoutComponent implements OnInit {
  private keycloakService = inject(KeycloakService);

  repositoryId = input.required({ transform: numberAttribute });

  repositoryQuery = injectQuery(() => ({
    ...getRepositoryByIdOptions({ path: { id: this.repositoryId() } }),
    enabled: () => !!this.repositoryId(),
  }));

  items!: { label: string; icon: string; path: string }[];

  logout() {
    this.keycloakService.logout();
  }

  ngOnInit() {
    this.items = [
      {
        label: 'CI/CD',
        icon: 'arrow-guide',
        path: 'ci-cd',
      },
      {
        label: 'Release Management',
        icon: 'rocket',
        path: 'release',
      },
      {
        label: 'Environments',
        icon: 'server-cog',
        path: 'environment/list',
      },
      {
        label: 'Project Settings',
        icon: 'adjustments-alt',
        path: 'settings',
      },
    ];
  }
}
