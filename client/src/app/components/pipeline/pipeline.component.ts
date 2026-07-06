import { Component, computed, input } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { PanelModule } from 'primeng/panel';
import { TooltipModule } from 'primeng/tooltip';
import { DividerModule } from 'primeng/divider';
import { SkeletonModule } from 'primeng/skeleton';
import { getPipelineByBranchOptions, getPipelineByPullRequestOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { PipelineDto } from '@app/core/modules/openapi';
import { GithubLinkButtonComponent } from '@app/components/github-link-button/github-link-button.component';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconCircleCheck, IconCircleDashed, IconCircleX, IconExclamationCircle, IconInfoCircle, IconMinus, IconProgress, IconProgressHelp } from 'angular-tabler-icons/icons';

export type PipelineSelector = { repositoryId: number } & (
  | {
      branchName: string;
    }
  | {
      pullRequestId: number;
    }
  | {
      workflowRunId: number;
    }
);

@Component({
  selector: 'app-pipeline',
  imports: [DividerModule, PanelModule, TablerIconComponent, TooltipModule, SkeletonModule, GithubLinkButtonComponent],
  providers: [
    provideTablerIcons({
      IconInfoCircle,
      IconCircleCheck,
      IconCircleX,
      IconCircleDashed,
      IconProgressHelp,
      IconProgress,
      IconMinus,
      IconExclamationCircle,
    }),
  ],
  templateUrl: './pipeline.component.html',
})
export class PipelineComponent {
  selector = input<PipelineSelector | null>();

  branchName = computed(() => {
    const selector = this.selector();
    if (!selector) return null;
    return 'branchName' in selector ? selector.branchName : null;
  });
  pullRequestId = computed(() => {
    const selector = this.selector();
    if (!selector) return null;
    return 'pullRequestId' in selector ? selector.pullRequestId : null;
  });

  // Canonical, always-visible pipeline (Build/Tests/Quality) resolved server-side from the
  // configured node catalog. One query per selector kind.
  // TODO instead of refetching every 15 seconds, we should use websockets for real-time updates
  branchPipelineQuery = injectQuery(() => ({
    ...getPipelineByBranchOptions({ query: { branch: this.branchName()! } }),
    enabled: this.branchName() !== null,
    refetchInterval: 15000,
  }));
  pullRequestPipelineQuery = injectQuery(() => ({
    ...getPipelineByPullRequestOptions({ path: { pullRequestId: this.pullRequestId() || 0 } }),
    enabled: this.pullRequestId() !== null,
    refetchInterval: 15000,
  }));

  private activeQuery = computed(() => (this.branchName() !== null ? this.branchPipelineQuery : this.pullRequestPipelineQuery));

  isPending = computed(() => this.activeQuery().isPending());

  pipeline = computed<PipelineDto>(() => this.activeQuery().data() ?? { categories: [] });

  categories = computed(() => this.pipeline().categories ?? []);

  hasCategories = computed(() => this.categories().some(category => (category.nodes ?? []).length > 0));
}
