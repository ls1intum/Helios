import { provideAppInitializer, ApplicationConfig, inject, provideExperimentalZonelessChangeDetection, ErrorHandler } from '@angular/core';
import { provideRouter, Router, withComponentInputBinding, withRouterConfig } from '@angular/router';
import { provideQueryClient, provideTanStackQuery, QueryClient, withDevtools } from '@tanstack/angular-query-experimental';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { providePrimeNG } from 'primeng/config';
import { definePreset } from '@primeng/themes';
import Aura from '@primeng/themes/aura';

import { routes } from './app.routes';
import { ConfirmationService, MessageService } from 'primeng/api';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { KeycloakService } from './core/services/keycloak/keycloak.service';
import { DatePipe } from '@angular/common';
import { RepositoryFilterGuard } from './core/middlewares/repository-filter.guard';
import * as Sentry from '@sentry/angular';
import { TraceService } from '@sentry/angular';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false, // default true
      staleTime: 1000 * 60 * 5, // 5 minutes
      retry: false,
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
          components: {
            toggleswitch: {
              colorScheme: {
                checkedBackground: '{emerald.500}',
                checkedHoverBackground: '{emerald.500}',
              },
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
    provideTanStackQuery(new QueryClient(), withDevtools()),
    RepositoryFilterGuard,
    MessageService,
    ConfirmationService,
    provideHttpClient(withInterceptorsFromDi()),
    provideAnimations(),
    provideQueryClient(queryClient),
    provideAppInitializer(() => {
      const keycloakService = inject(KeycloakService);
      return keycloakService.init();
    }),
    {
      provide: ErrorHandler,
      useValue: Sentry.createErrorHandler(),
    },
    { provide: TraceService, deps: [Router] },
    provideAppInitializer(() => {
      const initializerFn = (() => () => {
        inject(TraceService);
      })();
      return initializerFn();
    }),
  ],
};
