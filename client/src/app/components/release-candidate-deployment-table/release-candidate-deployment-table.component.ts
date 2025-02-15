import { Component, computed, inject, input, signal } from '@angular/core';
import { EnvironmentDto, ReleaseCandidateDetailsDto } from '@app/core/modules/openapi';
import {
  deployToEnvironmentMutation,
  getAllEnabledEnvironmentsOptions,
  getAllEnabledEnvironmentsQueryKey,
  getEnvironmentByIdQueryKey,
  getEnvironmentsByUserLockingQueryKey,
  getWorkflowRunUrlOptions,
  getWorkflowRunUrlQueryKey,
} from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';
import { IconsModule } from 'icons.module';
import { AvatarModule } from 'primeng/avatar';
import { BadgeModule } from 'primeng/badge';
import { ButtonModule } from 'primeng/button';
import { OverlayBadgeModule } from 'primeng/overlaybadge';
import { SkeletonModule } from 'primeng/skeleton';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';
import { MessageService } from 'primeng/api';
import { KeycloakService } from '@app/core/services/keycloak/keycloak.service';
import { PermissionService } from '@app/core/services/permission.service';
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
  isLoadingWorkflow = signal<boolean>(false);
  startQueryingWorkflow = signal<boolean>(false);
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
      // Trigger update on main layout after deployment
      this.queryClient.invalidateQueries({ queryKey: getEnvironmentsByUserLockingQueryKey() });
      this.queryClient.invalidateQueries({ queryKey: getAllEnabledEnvironmentsQueryKey() });
      this.queryClient.invalidateQueries({
        queryKey: getEnvironmentByIdQueryKey({
          path: { id: variables.body.environmentId },
        }),
      });
    },
  }));

  workflowRunUrlQuery = injectQuery(() => ({
    ...getWorkflowRunUrlOptions({
      query: {
        branch: this.releaseCandidate().branch.name,
        commitSha: this.releaseCandidate().commit.sha,
      },
      path: {
        environmentId: this.selectedEnvironmentId() ?? 0,
      },
    }),
    enabled: this.selectedEnvironmentId() !== undefined && this.deployToEnvironment.isSuccess() && this.startQueryingWorkflow(),
    refetchInterval: data => (data ? false : 3000),
  }));

  deployReleaseCandidate = (environment: EnvironmentDto) => {
    this.selectedEnvironmentId.set(environment.id);
    this.isLoadingWorkflow.set(true);
    this.startQueryingWorkflow.set(false);

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
            queryKey: getWorkflowRunUrlQueryKey({
              query: {
                branch: this.releaseCandidate().branch.name,
                commitSha: this.releaseCandidate().commit.sha,
              },
              path: {
                environmentId: environment.id,
              },
            }),
          });

          // Wait 10 seconds before starting to query the workflow URL
          setTimeout(() => {
            this.startQueryingWorkflow.set(true);
          }, 10 * 1000);
        },
        onError: () => {
          this.isLoadingWorkflow.set(false);
          this.selectedEnvironmentId.set(undefined);
          this.startQueryingWorkflow.set(false);
        },
      }
    );
  };

  deploymentStatus = (environment: EnvironmentDto) => {
    let deployments = this.releaseCandidate().deployments.filter(deployment => deployment.environment.id === environment.id);
    console.log('DEPLOYMENTS', deployments.length > 0 ? JSON.stringify(deployments.length) : 'NO DEPLOYMENTS');
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

    if (deployment.state !== undefined) {
      return deployment.state;
    }
    return 'UNKNOWN';
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
