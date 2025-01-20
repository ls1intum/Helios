import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { client } from './core/modules/openapi/sdk.gen';
import { environment } from '../environments/environment';
import { MessageService } from 'primeng/api';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  templateUrl: './app.component.html',
})
export class AppComponent {
  private keycloakService = inject(KeycloakService);
  private messageService = inject(MessageService);

  constructor() {
    client.setConfig({
      baseUrl: environment.serverUrl,
    });

    client.interceptors.request.use(request => {
      const token = this.keycloakService.keycloak.token;

      if (token) {
        request.headers.set('Authorization', `Bearer ${token}`);
      }
      return request;
    });

    client.interceptors.response.use(async response => {
      if (response.ok == false) {
        const errorMessage = await response.text();
        this.messageService.add({
          severity: 'error',
          summary: 'Fetch Error',
          detail: errorMessage || 'An unexpected error occurred.',
        });
      }
      return response;
    });
  }
}
