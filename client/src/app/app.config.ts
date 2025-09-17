import { provideAppInitializer, ApplicationConfig, inject, provideZonelessChangeDetection, ErrorHandler } from '@angular/core';
import { provideRouter, Router, withComponentInputBinding, withRouterConfig } from '@angular/router';
import { provideQueryClient, provideTanStackQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { providePrimeNG } from 'primeng/config';
import primeNGConfig from './primeng.config';

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
    providePrimeNG(primeNGConfig),
    provideZonelessChangeDetection(),
    provideRouter(routes, withComponentInputBinding(), withRouterConfig({ paramsInheritanceStrategy: 'always' })),
    provideAnimationsAsync(),
    provideTanStackQuery(new QueryClient()),
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
