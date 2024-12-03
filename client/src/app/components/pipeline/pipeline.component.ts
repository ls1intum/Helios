import { CommonModule } from '@angular/common';
import { Component, inject, input, InputSignal, Signal, signal } from '@angular/core';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { TableModule } from 'primeng/table';
import { tap } from 'rxjs';
import { Pipeline, PipelineService } from '@app/core/services/pipeline';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { PanelModule } from 'primeng/panel';
import { IconsModule } from 'icons.module';
import { TooltipModule } from 'primeng/tooltip';

@Component({
  selector: 'app-pipeline',
  imports: [CommonModule, TableModule, ProgressSpinnerModule, PanelModule, IconsModule, TooltipModule],
  providers: [PipelineService],
  templateUrl: './pipeline.component.html',
})
export class PipelineComponent {
  pipelineService = inject(PipelineService);

  pipeline = signal<Pipeline | null>(null);
  isLoading = signal(true);

  commitSha: InputSignal<string> = input('');
  branchName: InputSignal<string> = input('');
  pullRequestId: InputSignal<number|undefined> = input();

  query = injectQuery(() => ({
    queryKey: ['pipeline', this.commitSha(), this.branchName(), this.pullRequestId()],
    enabled: !!this.commitSha() && (!!this.branchName() || !!this.pullRequestId()),
    queryFn: () => {
      const commitSha = this.commitSha();
      console.log('commitSha', commitSha);
      const branchName = this.branchName();

      const pipeline = branchName ?
        this.pipelineService.getBranchPipeline(branchName, this.commitSha()) :
        this.pipelineService.getPullRequestPipeline(this.pullRequestId() || 0, this.commitSha());

      return pipeline
        .pipe(
          tap(pipeline => {
            this.pipeline.set(pipeline);
            this.isLoading.set(false);
          })
        ).subscribe()
      },
    refetchInterval: 5000, // Refetch every 5 seconds
  }));
}
