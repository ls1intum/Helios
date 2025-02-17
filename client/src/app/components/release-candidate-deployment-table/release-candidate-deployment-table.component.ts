import { Component, computed, inject, input, signal } from '@angular/core';
import { EnvironmentDto, ReleaseCandidateDetailsDto } from '@app/core/modules/openapi';
import {
  deployToEnvironmentMutation,
  getAllEnabledEnvironmentsOptions,
  getAllEnabledEnvironmentsQueryKey,
  getEnvironmentByIdQueryKey,
  getLatestDeploymentWorkflowRunOptions,
  getLatestDeploymentWorkflowRunQueryKey,
} from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { PermissionService } from '@app/core/services/permission.service';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { IconsModule } from 'icons.module';
import { MessageService } from 'primeng/api';
import { AvatarModule } from 'primeng/avatar';
import { BadgeModule } from 'primeng/badge';
import { ButtonModule } from 'primeng/button';
import { OverlayBadgeModule } from 'primeng/overlaybadge';
import { SkeletonModule } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { DeploymentStateTagComponent } from '../environments/deployment-state-tag/deployment-state-tag.component';

@Component({
  selector: 'app-release-candidate-deployment-table',
  imports: [TableModule, ButtonModule, IconsModule, SkeletonModule, TagModule, AvatarModule, OverlayBadgeModule, BadgeModule, TooltipModule, DeploymentStateTagComponent],
  templateUrl: './release-candidate-deployment-table.component.html',
})
export class ReleaseCandidateDeploymentTableComponent {
  releaseCandidate = input.required<ReleaseCandidateDetailsDto>();
  queryClient = inject(QueryClient);
  selectedEnvironmentId = signal<number | undefined>(undefined);
  messageService = inject(MessageService);
  keycloakService = inject(KeycloakService);
  permissions = inject(PermissionService);
  isLoggedIn = computed(() => this.keycloakService.isLoggedIn());

  userCanDeploy = computed(() => !!(this.isLoggedIn() && this.permissions.isAdmin()));

  environmentQuery = injectQuery(() => ({ ...getAllEnabledEnvironmentsOptions(), refetchInterval: 3000 }));

  deployableEnvironments = computed(() => this.environmentQuery.data()?.filter(environment => environment.type === 'STAGING' || environment.type === 'PRODUCTION'));

  deployToEnvironment = injectMutation(() => ({
    ...deployToEnvironmentMutation(),
    onSuccess: (_, variables) => {
      this.queryClient.invalidateQueries({ queryKey: getAllEnabledEnvironmentsQueryKey() });
      this.queryClient.invalidateQueries({
        queryKey: getEnvironmentByIdQueryKey({
          path: { id: variables.body.environmentId },
        }),
      });
    },
  }));

  workflowRunQuery = injectQuery(() => ({
    ...getLatestDeploymentWorkflowRunOptions({
      query: {
        branch: this.releaseCandidate().branch.name,
        commitSha: this.releaseCandidate().commit.sha,
        environmentId: this.selectedEnvironmentId() ?? 0,
      },
    }),
    enabled:
      this.selectedEnvironmentId() !== undefined &&
      this.deployToEnvironment.isSuccess() &&
      this.deployableEnvironments()?.find(environment => environment.id === this.selectedEnvironmentId()) !== undefined &&
      this.deploymentStatus(this.deployableEnvironments()!.find(environment => environment.id === this.selectedEnvironmentId())!) === 'WAITING',
  }));

  deployReleaseCandidate = (environment: EnvironmentDto) => {
    this.selectedEnvironmentId.set(environment.id);

    this.deployToEnvironment.mutate(
      {
        body: {
          environmentId: environment.id,
          branchName: this.releaseCandidate().branch.name,
          commitSha: this.releaseCandidate().commit.sha,
        },
      },
      {
        onSuccess: () => {
          this.messageService.add({ severity: 'success', summary: 'Success', detail: 'Deployment started successfully' });
          this.queryClient.invalidateQueries({
            queryKey: getLatestDeploymentWorkflowRunQueryKey({
              query: {
                branch: this.releaseCandidate().branch.name,
                commitSha: this.releaseCandidate().commit.sha,
                environmentId: environment.id,
              },
            }),
          });
        },
        onError: () => {
          this.selectedEnvironmentId.set(undefined);
        },
      }
    );
  };

  deploymentStatus = (environment: EnvironmentDto) => {
    let deployments = this.releaseCandidate().deployments.filter(deployment => deployment.environment.id === environment.id);
    // If there are no deployments related to this release candidate anymore, this release candidate was not deployed yet
    if (deployments.length === 0) {
      return 'NEVER_DEPLOYED';
    }

    // If the latest deployment is not part of the deployments list, but there were deployments to this environment, the release candidate was replaced
    if (deployments.every(deployment => environment.latestDeployment?.id !== deployment.id)) {
      return 'REPLACED';
    }

    // For the following cases, we are only interested in the deployment that matches the latest deployment of the environment
    const deployment = deployments.find(deployment => environment.latestDeployment?.id === deployment.id);

    // If there is no deployment that matches the latest deployment, the release candidate was replaced
    if (deployment === undefined) {
      return 'REPLACED';
    }

    return deployment.state || 'UNKNOWN';
  };

  openWorkflowUrl(url: string) {
    window.open(url, '_blank');
  }

  openEnvironment(event: MouseEvent, environment: EnvironmentDto): void {
    // Prevent the click event from propagating
    event.stopPropagation();

    // Only proceed if the server URL is available
    if (environment.serverUrl) {
      window.open(this.getFullUrl(environment.serverUrl), '_blank');
    }
  }

  private getFullUrl(url: string): string {
    if (url && !url.startsWith('http') && !url.startsWith('https')) {
      return 'http://' + url;
    }
    return url;
  }
}
