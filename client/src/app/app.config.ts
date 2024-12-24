import { provideAppInitializer, ApplicationConfig, inject, provideExperimentalZonelessChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding, withRouterConfig } from '@angular/router';
import { provideQueryClient, provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { providePrimeNG } from 'primeng/config';
import { definePreset } from '@primeng/themes';
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
});

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
        preset: definePreset(Aura, {
          semantic: {
            primary: {
              50: '{gray.50}',
              100: '{gray.100}',
              200: '{gray.200}',
              300: '{gray.300}',
              400: '{gray.400}',
              500: '{gray.500}',
              600: '{gray.600}',
              700: '{gray.700}',
              800: '{gray.800}',
              900: '{gray.900}',
              950: '{gray.950}',
            },
          },
        }),
        options: {
          darkModeSelector: '.dark-selector',
          cssLayer: {
            name: 'primeng',
            order: 'tailwind-base, primeng, tailwind-utilities',
          },
        },
      },
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
