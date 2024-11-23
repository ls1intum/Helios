import { Component, computed, inject, input } from '@angular/core';
import { FetchCommitService, Commit } from '@app/core/services/fetch/commit';
import { IconsModule } from 'icons.module';
import { TagModule } from 'primeng/tag';

@Component({
  selector: 'app-environment-commit-info',
  imports: [TagModule, IconsModule],
  providers: [FetchCommitService],
  templateUrl: './environment-commit-info.component.html',
  styleUrl: './environment-commit-info.component.css',
})
export class EnvironmentCommitInfoComponent {
  fetchEnvironments = inject(FetchCommitService);

  commits = this.fetchEnvironments.getCommits().data;
  commitHash = input.required<string>();

  commit = computed(() => this.commits()?.find((commit: Commit) => commit.commitHash === this.commitHash()));
}
