import { Component, computed, input } from '@angular/core';
import { MarkdownPipe } from '@app/core/modules/markdown/markdown.pipe';

import {PipelineComponent, PipelineSelector} from '@app/components/pipeline/pipeline.component';
import {TagModule} from 'primeng/tag';
import {IconsModule} from 'icons.module';
import {ButtonModule} from 'primeng/button';
import {InputTextModule} from 'primeng/inputtext';
import { DeploymentSelectionComponent } from '@app/components/deployment-selection/deployment-selection.component';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { SkeletonModule } from 'primeng/skeleton';
import { getPullRequestByRepositoryIdAndNumberOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';

@Component({
  selector: 'app-branch-details',
  imports: [InputTextModule, TagModule, IconsModule, ButtonModule, PipelineComponent, MarkdownPipe, DeploymentSelectionComponent, SkeletonModule],
  templateUrl: './pull-request-details.component.html',
})
export class PullRequestDetailsComponent {
  repositoryId = input.required<number>();
  pullRequestNumber = input.required<number>();

  query = injectQuery(() => ({
    ...getPullRequestByRepositoryIdAndNumberOptions({ path: { repoId: this.repositoryId(), number: this.pullRequestNumber() }}),
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
