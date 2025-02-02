import { Component, inject, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IconsModule } from 'icons.module';
import { User } from '@app/core/modules/openapi';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { AvatarModule } from 'primeng/avatar';
import { TooltipModule } from 'primeng/tooltip';

@Component({
  selector: 'app-user-avatar',
  imports: [CommonModule, TooltipModule, AvatarModule, IconsModule],
  templateUrl: './user-avatar.component.html',
})
export class UserAvatarComponent {
  keycloakService = inject(KeycloakService);

  user = input.required<User | undefined>();

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
