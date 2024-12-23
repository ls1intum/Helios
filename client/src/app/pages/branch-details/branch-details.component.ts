import { Component, computed, inject, input, signal, Signal } from '@angular/core';

import {PipelineComponent, PipelineSelector} from '@app/components/pipeline/pipeline.component';
import {TagModule} from 'primeng/tag';
import {IconsModule} from 'icons.module';
import {ButtonModule} from 'primeng/button';
import {InputTextModule} from 'primeng/inputtext';
import { DeploymentSelectionComponent } from '@app/components/deployment-selection/deployment-selection.component';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { SkeletonModule } from 'primeng/skeleton';
import { getBranchByRepositoryIdAndNameOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';

@Component({
  selector: 'app-branch-details',
  imports: [DeploymentSelectionComponent, InputTextModule, TagModule, IconsModule, ButtonModule, PipelineComponent, SkeletonModule],
  templateUrl: './branch-details.component.html',
})
export class BranchDetailsComponent {
  repositoryId = input.required<number>();
  branchName = input.required<string>();

  query = injectQuery(() => ({
    ...getBranchByRepositoryIdAndNameOptions({ path: { name: this.branchName(), repoId: this.repositoryId() }}),
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