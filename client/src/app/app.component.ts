import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { KeycloakService } from './core/services/keycloak/keycloak.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
export class AppComponent {}
