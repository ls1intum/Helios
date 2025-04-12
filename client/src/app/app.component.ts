import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { client } from '@app/core/modules/openapi/client.gen';
import { environment } from '../environments/environment';
import { MessageService } from 'primeng/api';
import { ReportProblemButtonComponent } from './components/report-problem-button/report-problem-button.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, ReportProblemButtonComponent],
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
      if (!response.ok) {
        // Attempt to parse the response as JSON
        let errorMessage = 'An unexpected error occurred.';
        try {
          const errorBody = await response.json();
          errorMessage = errorBody.message || errorMessage;
        } catch {
          // If parsing fails, fallback to text
          const errorText = await response.text();
          if (errorText) {
            errorMessage = errorText;
          }
        }
        // Parsing with status code
        if (response.status === 401) {
          this.messageService.add({
            severity: 'error',
            summary: 'Unauthorized',
            detail: 'You are unauthorized! Please refresh the page.',
          });
        } else {
          this.messageService.add({
            severity: 'error',
            summary: `Error ${response.status}`,
            detail: errorMessage,
          });
        }
      }
      return response;
    });
  }
}
