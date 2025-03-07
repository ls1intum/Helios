import { Component, inject, input } from '@angular/core';
import {
  deleteReleaseCandidateByNameMutation,
  evaluateMutation,
  getReleaseCandidateByNameOptions,
  getReleaseCandidateByNameQueryKey,
} from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { ButtonModule } from 'primeng/button';
import { ButtonGroupModule } from 'primeng/buttongroup';
import { SkeletonModule } from 'primeng/skeleton';
import { ReleaseCandidateDeploymentTableComponent } from '@app/components/release-candidate-deployment-table/release-candidate-deployment-table.component';
import { AvatarModule } from 'primeng/avatar';
import { IconsModule } from 'icons.module';
import { TimeAgoPipe } from '@app/pipes/time-ago.pipe';
import { TooltipModule } from 'primeng/tooltip';
import { SlicePipe } from '@angular/common';
import { TagModule } from 'primeng/tag';
import { ConfirmationService, MessageService } from 'primeng/api';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { PermissionService } from '@app/core/services/permission.service';
import { ReleaseCandidateDetailsDto } from '@app/core/modules/openapi';
import { ActivatedRoute, Router } from '@angular/router';

@Component({
  selector: 'app-release-candidate-details',
  imports: [SkeletonModule, ButtonModule, ReleaseCandidateDeploymentTableComponent, ButtonGroupModule, AvatarModule, IconsModule, TimeAgoPipe, TooltipModule, SlicePipe, TagModule],
  templateUrl: './release-candidate-details.component.html',
})
export class ReleaseCandidateDetailsComponent {
  private messageService = inject(MessageService);
  private keycloakService = inject(KeycloakService);
  permissionService = inject(PermissionService);
  private confirmationService = inject(ConfirmationService);
  private queryClient = inject(QueryClient);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  name = input.required<string>();
  releaseCandidateQuery = injectQuery(() => ({ ...getReleaseCandidateByNameOptions({ path: { name: this.name() } }), refetchInterval: 3000 }));

  evaluateReleaseCandidateMutation = injectMutation(() => ({
    ...evaluateMutation(),
    onSuccess: () => {
      this.messageService.add({ severity: 'success', summary: 'Release Candidate Evaluation', detail: 'Your evaluation has been saved successfully' });
      this.queryClient.invalidateQueries({ queryKey: getReleaseCandidateByNameQueryKey({ path: { name: this.name() } }) });
    },
  }));

  deleteReleaseCandidateMutation = injectMutation(() => ({
    ...deleteReleaseCandidateByNameMutation(),
    onSuccess: () => {
      this.messageService.add({ severity: 'success', summary: 'Release Candidate Deletion', detail: 'Release candidate has been deleted successfully' });
      this.router.navigate(['..'], { relativeTo: this.route });
    },
  }));

  evaluateReleaseCandidate = (isWorking: boolean) => {
    this.evaluateReleaseCandidateMutation.mutate({ path: { name: this.name(), isWorking } });
  };

  deleteReleaseCandidate = (rc: ReleaseCandidateDetailsDto) => {
    this.confirmationService.confirm({
      header: 'Delete Release Candidate',
      message: `Are you sure you want to delete release candidate ${rc.name}? This cannot be undone.`,
      accept: () => {
        this.deleteReleaseCandidateMutation.mutate({ path: { name: rc.name } });
      },
    });
  };

  hasUserEvaluatedTo(isWorking: boolean) {
    const evaluations = this.releaseCandidateQuery.data()?.evaluations;
    if (!evaluations) return false;
    const userEvaluation = evaluations.find(evaluation => evaluation.user.login.toLowerCase() === this.keycloakService.getPreferredUsername()?.toLowerCase());
    return userEvaluation?.isWorking === isWorking;
  }
}
