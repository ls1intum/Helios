import { Component, computed, inject, input, signal } from '@angular/core';
import { EnvironmentDto, ReleaseInfoDetailsDto } from '@app/core/modules/openapi';
import {
  deployToEnvironmentMutation,
  getAllEnabledEnvironmentsOptions,
  getAllEnabledEnvironmentsQueryKey,
  getEnvironmentByIdQueryKey,
  getReleaseInfoByNameQueryKey,
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
  releaseCandidate = input.required<ReleaseInfoDetailsDto>();
  queryClient = inject(QueryClient);
  selectedEnvironmentId = signal<number | undefined>(undefined);
  messageService = inject(MessageService);
  keycloakService = inject(KeycloakService);
  permissions = inject(PermissionService);
  isLoggedIn = computed(() => this.keycloakService.isLoggedIn());

  userCanDeploy = computed(() => !!(this.isLoggedIn() && this.permissions.isAdmin()));

  environmentQuery = injectQuery(() => ({ ...getAllEnabledEnvironmentsOptions(), refetchInterval: 3000 }));

  groupedEnvironments = computed(() => {
    const environments = this.environmentQuery.data() || [];
    return environments.map(env => ({ ...env, type: env.type || 'Ungrouped' }));
  });

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

  deployReleaseCandidate = (environment: EnvironmentDto) => {
    this.selectedEnvironmentId.set(environment.id);

    this.deployToEnvironment.mutate(
      {
        body: {
          environmentId: environment.id,
          branchName: this.releaseCandidate().branch?.name,
          commitSha: this.releaseCandidate().commit.sha,
        },
      },
      {
        onSuccess: () => {
          this.queryClient.invalidateQueries({ queryKey: getAllEnabledEnvironmentsQueryKey() });
          this.queryClient.invalidateQueries({ queryKey: getReleaseInfoByNameQueryKey({ path: { name: this.releaseCandidate().name } }) });

          this.messageService.add({ severity: 'success', summary: 'Success', detail: 'Deployment started successfully' });
        },
        onError: () => {
          this.selectedEnvironmentId.set(undefined);
        },
      }
    );
  };

  deploymentStatus = (environment: EnvironmentDto) => {
    let deployments = this.releaseCandidate().deployments.filter(deployment => deployment.environmentId === environment.id);

    // Get latest one where sha is the same as the release candidate

    // If there are no deployments related to this release candidate anymore, this release candidate was not deployed yet
    if (deployments.length === 0) {
      return 'NEVER_DEPLOYED';
    }

    // If the latest deployment is not part of the deployments list, but there were deployments to this environment, the release candidate was replaced
    if (deployments.every(deployment => environment.latestDeployment?.id !== deployment.id || environment.latestDeployment?.type !== deployment.type)) {
      return 'REPLACED';
    }

    // If we were not replaced and we are deployed, we are the latest deployment
    return environment.latestDeployment?.state || 'UNKNOWN';
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

  capitalizeFirstLetter(string: string): string {
    return string.charAt(0).toUpperCase() + string.slice(1).toLowerCase();
  }

  private getFullUrl(url: string): string {
    if (url && !url.startsWith('http') && !url.startsWith('https')) {
      return 'http://' + url;
    }
    return url;
  }
}
