import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { IconsModule } from 'icons.module';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { HeliosIconComponent } from '../../components/helios-icon/helios-icon.component';
import { DividerModule } from 'primeng/divider';
import { AvatarModule } from 'primeng/avatar';
import { ToastModule } from 'primeng/toast';
import {KeycloakService} from '@app/core/services/keycloak/keycloak.service';

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, ToastModule, RouterLinkActive, IconsModule, ButtonModule, CommonModule, TooltipModule, HeliosIconComponent, DividerModule, AvatarModule],
  templateUrl: './main-layout.component.html',
})
export class MainLayoutComponent implements OnInit {
  items!: { label: string; icon: string; path: string }[];


  constructor(private keycloakService: KeycloakService) {}

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
