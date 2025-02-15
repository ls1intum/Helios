import { Component, computed, inject, input, signal } from '@angular/core';
import { EnvironmentDto, ReleaseCandidateDetailsDto } from '@app/core/modules/openapi';
import {
  deployToEnvironmentMutation,
  getAllEnabledEnvironmentsOptions,
  getAllEnabledEnvironmentsQueryKey,
  getEnvironmentByIdQueryKey,
  getEnvironmentsByUserLockingQueryKey,
  getWorkflowRunUrlOptions,
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

@Component({
  selector: 'app-release-candidate-deployment-table',
  imports: [TableModule, ButtonModule, IconsModule, SkeletonModule, TagModule, AvatarModule, OverlayBadgeModule, BadgeModule, TooltipModule],
  templateUrl: './release-candidate-deployment-table.component.html',
})
export class ReleaseCandidateDeploymentTableComponent {
  releaseCandidate = input.required<ReleaseCandidateDetailsDto>();
  queryClient = inject(QueryClient);
  selectedEnvironmentId = signal<number | undefined>(undefined);

  environmentQuery = injectQuery(() => getAllEnabledEnvironmentsOptions());

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
      query: { branch: this.releaseCandidate().branch.name, commitSha: this.releaseCandidate().commit.sha },
      path: { environmentId: this.selectedEnvironmentId() || 0 },
    }),
    enabled: this.selectedEnvironmentId() !== undefined,
    refetchInterval: data => (data ? false : 3000),
    retry: 5, // Retry up to 5 times
    retryDelay: 3000,
  }));

  deployReleaseCandidate = (environment: EnvironmentDto) => {
    this.deployToEnvironment.mutate(
      { body: { environmentId: environment.id, branchName: this.releaseCandidate().branch.name, commitSha: this.releaseCandidate().commit.sha } },
      {
        onSuccess: () => {
          // Wait 3 seconds before enabling the workflow URL query
          setTimeout(() => {
            this.selectedEnvironmentId.set(environment.id);
          }, 5000);
        },
      }
    );
  };

  isLoadingWorkflowUrl = computed(() => {
    return this.workflowRunUrlQuery.isLoading() || (this.deployToEnvironment.isSuccess() && !this.workflowRunUrlQuery.data());
  });

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

    if (deployment.state === 'SUCCESS') {
      return 'SUCCESS';
    }

    if (deployment.state === 'FAILURE') {
      return 'FAILURE';
    }
    return 'UNKNOWN';
  };

  openWorkflowUrl(url: string) {
    window.open(url, '_blank');
  }
}
