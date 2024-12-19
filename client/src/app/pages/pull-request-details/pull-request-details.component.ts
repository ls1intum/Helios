import { Component, computed, inject, input, Signal } from '@angular/core';
import { MarkdownPipe } from '@app/core/modules/markdown/markdown.pipe';

import {PipelineComponent, PipelineSelector} from '@app/components/pipeline/pipeline.component';
import {TagModule} from 'primeng/tag';
import {IconsModule} from 'icons.module';
import {ButtonModule} from 'primeng/button';
import {InputTextModule} from 'primeng/inputtext';
import { DeploymentSelectionComponent } from '@app/components/deployment-selection/deployment-selection.component';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { PullRequestControllerService } from '@app/core/modules/openapi';
import { lastValueFrom } from 'rxjs';
import { SkeletonModule } from 'primeng/skeleton';

@Component({
  selector: 'app-branch-details',
  imports: [InputTextModule, TagModule, IconsModule, ButtonModule, PipelineComponent, MarkdownPipe, DeploymentSelectionComponent, SkeletonModule],
  templateUrl: './pull-request-details.component.html',
})
export class PullRequestDetailsComponent {
  pullRequestService = inject(PullRequestControllerService);

  repositoryId = input.required<number>();
  pullRequestNumber = input.required<number>();

  query = injectQuery(() => ({
    queryKey: ['pullRequest', this.repositoryId(), this.pullRequestNumber()],
    queryFn: () => lastValueFrom(
      this.pullRequestService.getPullRequestByRepositoryIdAndNumber(this.repositoryId(), this.pullRequestNumber())
    ),
    refetchInterval: 5000,
  }));

  pipelineSelector = computed<PipelineSelector | null>(() => {
    const pr = this.query.data();

    if (!pr) {
      return null;
    }

    return {
      repositoryId: this.repositoryId(),
      pullRequestId: pr.id,
    };
  });
}
