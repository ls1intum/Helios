import { NgModule, provideZonelessChangeDetection } from '@angular/core';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ConfirmationService, MessageService } from 'primeng/api';
import { KeycloakService } from './core/services/keycloak/keycloak.service';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { PermissionService } from './core/services/permission.service';
import { provideQueryClient, QueryClient } from '@tanstack/angular-query-experimental';
import { DatePipe } from '@angular/common';

@NgModule({
  providers: [
    provideZonelessChangeDetection(),
    provideNoopAnimations(),
    MessageService,
    ConfirmationService,
    KeycloakService,
    { provide: Router, useValue: { navigate: () => {} } },
    { provide: ActivatedRoute, useValue: { parent: '', snapshot: { firstChild: undefined } } },
    RouterLink,
    Router,
    PermissionService,
    DatePipe,
    provideQueryClient(new QueryClient()),
  ],
})
export class TestModule {}
