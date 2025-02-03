import { Component, inject, input } from '@angular/core';
import { evaluateMutation, getTagByNameOptions, getTagByNameQueryKey } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
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

  evaluateTagMutation = injectMutation(() => ({
    ...evaluateMutation(),
    onSuccess: () => {
      this.messageService.add({ severity: 'success', summary: 'Tag Evaluation', detail: 'Your evaluation has been saved successfully' });
      this.queryClient.invalidateQueries({ queryKey: getTagByNameQueryKey({ path: { name: this.name() } }) });
    },
  }));

  evaluateTag = (isWorking: boolean) => {
    this.evaluateTagMutation.mutate({ path: { name: this.name(), isWorking } });
  };
}
