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
import { TimeAgoPipe } from '@app/pipes/time-ago.pipe';

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
  imports: [DividerModule, PanelModule, TablerIconComponent, TooltipModule, SkeletonModule, GithubLinkButtonComponent, NgTemplateOutlet, TimeAgoPipe],
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

  // The last definitive (pass/fail) result in recent history, shown as a confidence footer while
  // the displayed commit is still running. The server walks past inconclusive commits (cancelled/
  // superseded/skipped) and omits it entirely once the displayed commit is terminal, so no
  // client-side guard is needed.
  previous = computed(() => this.pipeline().previous ?? null);

  // Terminal (and awaiting-approval) conclusions → icon + tooltip. Colour comes from the shared
  // status map so it stays consistent with the rest of the app.
  private static readonly ICON_BY_CONCLUSION: Record<string, { name: string; tooltip: string }> = {
    FAILURE: { name: 'circle-x', tooltip: 'Failed' },
    TIMED_OUT: { name: 'circle-x', tooltip: 'Failed' },
    STARTUP_FAILURE: { name: 'circle-x', tooltip: 'Failed' },
    SUCCESS: { name: 'circle-check', tooltip: 'Passed' },
    SKIPPED: { name: 'circle-minus', tooltip: 'Skipped' },
    CANCELLED: { name: 'ban', tooltip: 'Cancelled' },
    ACTION_REQUIRED: { name: 'player-pause', tooltip: 'Waiting for approval' },
    NEUTRAL: { name: 'progress-help', tooltip: 'No result' },
  };

  // Warning colour (orange), forced for the awaiting-approval states so a bare WAITING (no
  // conclusion) matches the ACTION_REQUIRED conclusion rather than the yellow in-progress bucket.
  private static readonly WARNING_ICON = getStatusColors('ACTION_REQUIRED').icon;

  // A job-less node's state, inferred from its run. Queued is a muted clock (scheduled, distinct
  // from the running spinner); awaiting-approval is the warning pause; absent is "not running yet".
  private static readonly ICON_BY_STATUS: Record<string, { name: string; class: string; tooltip: string }> = {
    ACTION_REQUIRED: { name: 'player-pause', class: PipelineComponent.WARNING_ICON, tooltip: 'Waiting for approval' },
    WAITING: { name: 'player-pause', class: PipelineComponent.WARNING_ICON, tooltip: 'Waiting for approval' },
    QUEUED: { name: 'clock', class: 'text-muted-color', tooltip: 'Queued' },
    PENDING: { name: 'circle-dashed', class: 'text-muted-color', tooltip: 'Not running yet' },
    REQUESTED: { name: 'circle-dashed', class: 'text-muted-color', tooltip: 'Not running yet' },
  };

  /**
   * Resolves a node's aggregated `{status, conclusion}` to its icon. Conclusion is checked before
   * status so a fail-fast node (still running, but a leg already failed) shows red rather than a
   * spinner; running spins yellow; every other transient state is table-driven and distinct.
   */
  nodeIcon(node: { status?: string | null; conclusion?: string | null }): { name: string; class: string; tooltip: string } {
    const { status, conclusion } = node;
    const byConclusion = conclusion ? PipelineComponent.ICON_BY_CONCLUSION[conclusion] : undefined;
    if (byConclusion) return { ...byConclusion, class: getStatusColors(conclusion, status).icon };
    if (status === 'IN_PROGRESS') {
      return { name: 'progress', class: `${getStatusColors(conclusion, status).icon} animate-spin`, tooltip: 'Running' };
    }
    return (status ? PipelineComponent.ICON_BY_STATUS[status] : undefined) ?? { name: 'progress-help', class: 'text-muted-color', tooltip: 'Unknown' };
  }
}
