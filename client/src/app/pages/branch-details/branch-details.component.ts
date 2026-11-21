import { Component, computed, input, signal, ChangeDetectionStrategy } from '@angular/core';

import { PipelineComponent, PipelineSelector } from '@app/components/pipeline/pipeline.component';
import { InputTextModule } from 'primeng/inputtext';
import { DeploymentSelectionComponent } from '@app/components/deployment-selection/deployment-selection.component';
import { injectQuery } from '@tanstack/angular-query-experimental';
import { SkeletonModule } from 'primeng/skeleton';
import { getBranchByRepositoryIdAndNameOptions, getCommitByRepositoryIdAndNameOptions } from '@app/core/modules/openapi/@tanstack/angular-query-experimental.gen';
import { MarkdownPipe } from '@app/core/modules/markdown/markdown.pipe';
import { provideTablerIcons, TablerIconComponent } from 'angular-tabler-icons';
import { IconGitBranch } from 'angular-tabler-icons/icons';
import { Divider } from 'primeng/divider';
import { DeploymentHistoryComponent } from '@app/components/deployment-history/deployment-history.component';
import { Tab, TabList, TabPanel, TabPanels, Tabs } from 'primeng/tabs';

@Component({
  selector: 'app-branch-details',
  imports: [
    DeploymentSelectionComponent,
    InputTextModule,
    TablerIconComponent,
    PipelineComponent,
    SkeletonModule,
    MarkdownPipe,
    Divider,
    DeploymentHistoryComponent,
    Tab,
    TabList,
    TabPanel,
    TabPanels,
    Tabs,
  ],
  providers: [
    provideTablerIcons({
      IconGitBranch,
    }),
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './branch-details.component.html',
})
export class BranchDetailsComponent {
  repositoryId = input.required<number>();
  branchName = input.required<string>();
  deploymentTab = signal<'deploy' | 'history'>('deploy');

  onDeploymentTabChange(value: string | number | undefined): void {
    if (value === 'deploy' || value === 'history') {
      this.deploymentTab.set(value);
    }
  }

  query = injectQuery(() => ({
    ...getBranchByRepositoryIdAndNameOptions({ path: { repoId: this.repositoryId() }, query: { name: this.branchName() } }),
    refetchInterval: 30000,
  }));

  commitQuery = injectQuery(() => ({
    ...getCommitByRepositoryIdAndNameOptions({ path: { repoId: this.repositoryId(), sha: this.query.data()?.commitSha || '' } }),
    enabled: !!this.repositoryId() && !!this.query.data(),
  }));
  commit = computed(() => this.commitQuery.data());

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
