import { Component, computed, inject, input, signal, Signal } from '@angular/core';

import {PipelineComponent, PipelineSelector} from '@app/components/pipeline/pipeline.component';
import {TagModule} from 'primeng/tag';
import {IconsModule} from 'icons.module';
import {ButtonModule} from 'primeng/button';
import {InputTextModule} from 'primeng/inputtext';
import { DeploymentSelectionComponent } from '@app/components/deployment-selection/deployment-selection.component';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { lastValueFrom, map } from 'rxjs';
import { BranchControllerService } from '@app/core/modules/openapi';
import { SkeletonModule } from 'primeng/skeleton';

@Component({
  selector: 'app-branch-details',
  imports: [DeploymentSelectionComponent, InputTextModule, TagModule, IconsModule, ButtonModule, PipelineComponent, SkeletonModule],
  templateUrl: './branch-details.component.html',
})
export class BranchDetailsComponent {
  branchService = inject(BranchControllerService);

  repositoryId = input.required<number>();
  branchName = input.required<string>();

  query = injectQuery(() => ({
    queryKey: ['branch', this.repositoryId(), this.branchName()],
    queryFn: () => lastValueFrom(
      this.branchService.getBranchByRepositoryIdAndName(this.repositoryId()!, this.branchName())
    ),
    refetchInterval: 5000,
  }));

  pipelineSelector = computed<PipelineSelector | null>(() => {
    const branch = this.query.data();

    if (!branch) {
      return null;
    }

    return {
      repositoryId: this.repositoryId()!,
      branchName: branch.name,
    };
  });
}
