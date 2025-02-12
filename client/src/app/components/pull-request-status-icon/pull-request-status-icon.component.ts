import { Component, computed, input } from '@angular/core';
import { TablerIconComponent } from 'angular-tabler-icons';
import { Tooltip } from 'primeng/tooltip';
import { PullRequestInfoDto } from '@app/core/modules/openapi';


@Component({
  selector: 'app-pull-request-status-icon',
  imports: [TablerIconComponent, Tooltip],
  templateUrl: './pull-request-status-icon.component.html',
})
export class PullRequestStatusIconComponent {
  pullRequest = input<PullRequestInfoDto | null>();
  tooltipPosition = input<Tooltip['tooltipPosition']>('right');

  iconName = computed(() => {
    if (!this.pullRequest()) return 'question-mark';
    if (this.pullRequest()?.isMerged) return 'git-merge';
    if (this.pullRequest()?.state === 'CLOSED') return 'git-pull-request-closed';
    if (this.pullRequest()?.isDraft) return 'git-pull-request-draft';
    return 'git-pull-request'; // Default for open PRs
  });

  iconColor = computed(() => {
    if (!this.pullRequest()) return 'text-gray-400';
    if (this.pullRequest()?.isMerged) return 'text-purple-500';
    if (this.pullRequest()?.state === 'CLOSED') return 'text-red-500';
    if (this.pullRequest()?.isDraft) return 'text-gray-600';
    return 'text-green-600'; // Default for open PRs
  });

  tooltipText = computed(() => {
    if (!this.pullRequest()) return 'Unknown PR status';
    if (this.pullRequest()?.isMerged) return 'Merged';
    if (this.pullRequest()?.state === 'CLOSED') return 'Closed';
    if (this.pullRequest()?.isDraft) return 'Draft';
    return 'Open';
  });
}
