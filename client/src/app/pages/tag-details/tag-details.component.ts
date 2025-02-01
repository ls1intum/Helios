import { Component, inject, input } from '@angular/core';
import { getTagByNameOptions, getTagByNameQueryKey, markBrokenMutation, markWorkingMutation } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { ButtonModule } from 'primeng/button';
import { ButtonGroupModule } from 'primeng/buttongroup';
import { SkeletonModule } from 'primeng/skeleton';
import { TagDeploymentTableComponent } from '@app/components/tag-deployment-table/tag-deployment-table.component';
import { AvatarModule } from 'primeng/avatar';
import { IconsModule } from 'icons.module';
import { TimeAgoPipe } from '@app/pipes/time-ago.pipe';
import { TooltipModule } from 'primeng/tooltip';
import { SlicePipe } from '@angular/common';
import { TagModule } from 'primeng/tag';
import { MessageService } from 'primeng/api';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';

@Component({
  selector: 'app-tag-details',
  imports: [SkeletonModule, ButtonModule, TagDeploymentTableComponent, ButtonGroupModule, AvatarModule, IconsModule, TimeAgoPipe, TooltipModule, SlicePipe, TagModule],
  templateUrl: './tag-details.component.html',
})
export class TagDetailsComponent {
  private messageService = inject(MessageService);
  private keycloakService = inject(KeycloakService);
  private queryClient = inject(QueryClient);

  name = input.required<string>();
  tagQuery = injectQuery(() => getTagByNameOptions({ path: { name: this.name() } }));

  markWorkingMutation = injectMutation(() => ({
    ...markWorkingMutation(),
    onSuccess: () => {
      this.messageService.add({ severity: 'success', summary: 'Mark as working', detail: 'Tag has been marked as working successfully' });
      this.queryClient.invalidateQueries({ queryKey: getTagByNameQueryKey({ path: { name: this.name() } }) });
    },
  }));
  markBrokenMutation = injectMutation(() => ({
    ...markBrokenMutation(),
    onSuccess: () => {
      this.messageService.add({ severity: 'success', summary: 'Mark as broken', detail: 'Tag has been marked as broken successfully' });
      this.queryClient.invalidateQueries({ queryKey: getTagByNameQueryKey({ path: { name: this.name() } }) });
    },
  }));

  markWorking = () => {
    this.markWorkingMutation.mutate({ body: this.keycloakService.getPreferredUsername(), path: { name: this.name() } });
  };

  markBroken = () => {
    this.markBrokenMutation.mutate({ body: this.keycloakService.getPreferredUsername(), path: { name: this.name() } });
  };
}
