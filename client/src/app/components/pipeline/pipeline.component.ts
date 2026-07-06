import { NgTemplateOutlet } from '@angular/common';
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
import { IconBan, IconCircleCheck, IconCircleDashed, IconCircleMinus, IconCircleX, IconExclamationCircle, IconProgress, IconProgressHelp } from 'angular-tabler-icons/icons';
import { getStatusColors } from '@app/core/utils/status-colors';

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
  imports: [DividerModule, PanelModule, TablerIconComponent, TooltipModule, SkeletonModule, GithubLinkButtonComponent, NgTemplateOutlet],
  providers: [
    provideTablerIcons({
      IconCircleCheck,
      IconCircleX,
      IconCircleDashed,
      IconCircleMinus,
      IconBan,
      IconProgressHelp,
      IconProgress,
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

  // Overall merge-readiness node (e.g. Artemis' "All required CI Passed"), rendered as a header
  // badge. Null for repos/pipelines without a configured gate.
  gate = computed(() => this.pipeline().gate ?? null);

  /**
   * Resolves a node's aggregated `{status, conclusion}` to its icon, reusing the shared status
   * colour map. Conclusion is checked before status so a fail-fast node (still running, but a leg
   * already failed) shows red rather than a spinner. "Not running yet" is deliberately muted
   * instead of the shared map's queued=in_progress colour — separating queued from running is the
   * whole point of this view.
   */
  nodeIcon(node: { status?: string | null; conclusion?: string | null }): { name: string; class: string; tooltip: string } {
    const { status, conclusion } = node;
    const color = getStatusColors(conclusion, status).icon;
    switch (conclusion) {
      case 'FAILURE':
      case 'TIMED_OUT':
      case 'STARTUP_FAILURE':
        return { name: 'circle-x', class: color, tooltip: 'Failed' };
      case 'SUCCESS':
        return { name: 'circle-check', class: color, tooltip: 'Passed' };
      case 'SKIPPED':
        return { name: 'circle-minus', class: color, tooltip: 'Skipped' };
      case 'CANCELLED':
        return { name: 'ban', class: color, tooltip: 'Cancelled' };
      case 'NEUTRAL':
        return { name: 'progress-help', class: color, tooltip: 'Neutral' };
    }
    if (status === 'IN_PROGRESS') return { name: 'progress', class: `${color} animate-spin`, tooltip: 'Running' };
    if (status === 'PENDING' || status === 'QUEUED' || status === 'WAITING' || status === 'REQUESTED') {
      return { name: 'circle-dashed', class: 'text-muted-color', tooltip: 'Not running yet' };
    }
    return { name: 'progress-help', class: 'text-muted-color', tooltip: 'Unknown' };
  }
}
