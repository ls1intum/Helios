import { CommonModule } from '@angular/common';
import { Component, inject, input, signal } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { TableModule } from 'primeng/table';
import { lastValueFrom, tap } from 'rxjs';
import { Pipeline, PipelineService } from '@app/core/services/pipeline';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { PanelModule } from 'primeng/panel';
import { IconsModule } from 'icons.module';
import { TooltipModule } from 'primeng/tooltip';
import { SkeletonModule } from 'primeng/skeleton';

export type PipelineSelector = { repositoryId: number } & ({
  branchName: string;
} | {
  pullRequestId: number;
});

@Component({
  selector: 'app-pipeline',
  imports: [CommonModule, TableModule, ProgressSpinnerModule, PanelModule, IconsModule, TooltipModule, SkeletonModule],
  providers: [PipelineService],
  templateUrl: './pipeline.component.html',
})
export class PipelineComponent {
  pipelineService = inject(PipelineService);

  selector = input<PipelineSelector | null>();

  query = injectQuery(() => ({
    queryKey: ['pipeline', this.selector()],
    enabled: !!this.selector(),
    queryFn: () => {
      const selector = this.selector()!;

      const pipeline = 'branchName' in selector ?
        this.pipelineService.getBranchPipeline(selector.branchName) :
        this.pipelineService.getPullRequestPipeline(selector.pullRequestId);

      return lastValueFrom(pipeline);
    },
    refetchInterval: 2000, // Refetch every 2 seconds
  }));
}
