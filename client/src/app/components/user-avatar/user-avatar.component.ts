import { Component, inject, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IconsModule } from 'icons.module';
import { UserInfoDto } from '@app/core/modules/openapi';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { AvatarModule } from 'primeng/avatar';
import { TooltipModule } from 'primeng/tooltip';

@Component({
  selector: 'app-user-avatar',
  imports: [CommonModule, TooltipModule, AvatarModule, IconsModule],
  templateUrl: './user-avatar.component.html',
})
export class UserAvatarComponent {
  private keycloakService = inject(KeycloakService);

  user = input.required<UserInfoDto | undefined>();
  toolTipText = input<string | undefined>();

  getAvatarBorderClass(login: string) {
    return this.keycloakService.isCurrentUser(login) ? 'border-2 border-primary-400 rounded-full' : '';
  }

  openUserProfile(login: string) {
    //Redirect to the user's github profile
    window.open(`
      https://www.github.com/${login}
    `);
  }

  getTooltipText(): string {
    const tooltipText = this.toolTipText() ?? '';
    const userText = this.keycloakService.isCurrentUser(this.user()?.login) ? 'You' : (this.user()?.name ?? '');
    return `${tooltipText} ${userText}`.trim();
  }
}
