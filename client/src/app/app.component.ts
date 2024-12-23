import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import {KeycloakService} from '@app/core/services/keycloak/keycloak.service';
import { client } from './core/modules/openapi/sdk.gen';
import { environment } from '../environments/environment';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  templateUrl: './app.component.html',
})
export class AppComponent {

  constructor(private keycloakService: KeycloakService) {
    const token = this.keycloakService.keycloak.token;

    client.setConfig({
      baseUrl: environment.serverUrl,
    });

    client.interceptors.request.use((request, options) => {
      request.headers.set('Authorization', `Bearer ${token}`);
      return request;
    });
  }
}
