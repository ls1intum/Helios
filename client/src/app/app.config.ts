import { provideAppInitializer, ApplicationConfig, inject, provideExperimentalZonelessChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding, withRouterConfig } from '@angular/router';
import { provideQueryClient, provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { providePrimeNG } from 'primeng/config';
import Aura from '@primeng/themes/aura';

import { routes } from './app.routes';
import { MessageService } from 'primeng/api';
import { HTTP_INTERCEPTORS, provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { client } from './core/modules/openapi';
import { environment } from 'environments/environment';
import { KeycloakService } from './core/services/keycloak/keycloak.service';
import { BearerInterceptor } from './core/services/keycloak/bearer-interceptor';
import { DatePipe } from '@angular/common';


client.setConfig({
  baseUrl: environment.serverUrl,
})

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false, // default true
      staleTime: 1000 * 60 * 5, // 5 minutes
      retry: 2,
    },
  },
});

export const appConfig: ApplicationConfig = {
  providers: [
    DatePipe,
    providePrimeNG({
      theme: {
        preset: Aura,
        options: {
          darkModeSelector: '.dark-selector',
          cssLayer: {
            name: 'primeng',
            order: 'tailwind-base, primeng, tailwind-utilities'
          }
        }
      }
    }),
    provideExperimentalZonelessChangeDetection(),
    provideRouter(routes, withComponentInputBinding(), withRouterConfig({ paramsInheritanceStrategy: 'always' })),
    provideAnimationsAsync(),
    provideTanStackQuery(new QueryClient()),
    MessageService,
    provideHttpClient(withInterceptorsFromDi()),
    provideAnimations(),
    provideQueryClient(queryClient),
    provideAppInitializer(() => {
      const keycloakService = inject(KeycloakService);
      return keycloakService.init();
    }),
    { provide: HTTP_INTERCEPTORS, useClass: BearerInterceptor, multi: true },
  ],
};
