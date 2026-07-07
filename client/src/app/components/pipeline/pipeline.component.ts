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
import {
  IconBan,
  IconCircleCheck,
  IconCircleDashed,
  IconCircleMinus,
  IconCircleX,
  IconClock,
  IconExclamationCircle,
  IconGitCommit,
  IconPlayerPause,
  IconProgress,
  IconProgressHelp,
} from 'angular-tabler-icons/icons';
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
      IconClock,
      IconPlayerPause,
      IconGitCommit,
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

  // The commit the node states reflect, and whether it is the branch/PR head — the freshness anchor
  // that makes the view trustworthy ("up to date" vs "newest commit not built yet").
  head = computed(() => this.pipeline().head ?? null);

  // The immediately-preceding commit's outcome, shown as a confidence footer while the displayed
  // commit is still running. Null once the displayed commit is terminal (its own row says it all).
  previous = computed(() => this.pipeline().previous ?? null);

  /**
   * Resolves a node's aggregated `{status, conclusion}` to its icon, reusing the shared status
   * colour map. Conclusion is checked before status so a fail-fast node (still running, but a leg
   * already failed) shows red rather than a spinner.
   *
   * The transient states are deliberately distinct so the view is honest and trustworthy: a job
   * awaiting a maintainer's approval shows "Waiting for approval" (actionable), a scheduled-but-not-
   * started job shows a "Queued" clock (not idle), and only a genuinely absent CI run reads as the
   * muted "Not running yet".
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
      case 'ACTION_REQUIRED':
        return { name: 'player-pause', class: color, tooltip: 'Waiting for approval' };
      case 'NEUTRAL':
        return { name: 'progress-help', class: color, tooltip: 'Neutral' };
    }
    if (status === 'IN_PROGRESS') return { name: 'progress', class: `${color} animate-spin`, tooltip: 'Running' };
    if (status === 'ACTION_REQUIRED' || status === 'WAITING') {
      return { name: 'player-pause', class: color, tooltip: 'Waiting for approval' };
    }
    if (status === 'QUEUED') return { name: 'clock', class: color, tooltip: 'Queued' };
    if (status === 'PENDING' || status === 'REQUESTED') {
      return { name: 'circle-dashed', class: 'text-muted-color', tooltip: 'Not running yet' };
    }
    return { name: 'progress-help', class: 'text-muted-color', tooltip: 'Unknown' };
  }
}
